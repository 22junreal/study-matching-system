#!/usr/bin/env python3
"""Restart only the isolated monitoring backend and prove persisted refresh sessions survive."""
import datetime
import hashlib
import http.client
import json
from pathlib import Path
import secrets
import signal
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
STATE=Path('/private/tmp' if Path('/private/tmp').exists() else tempfile.gettempdir())/'study-monitoring'
COMPOSE=['docker','compose','--env-file',str(STATE/'environment'),'-p','study-monitoring',
         '-f',str(ROOT/'infra/compose.prod.yml'),'-f',str(ROOT/'infra/compose.monitoring.yml'),
         '-f',str(ROOT/'infra/compose.edge.yml')]
REPORT=ROOT/'reports/verification/backend-restart.json'
TABLES=['members','profiles','studies','study_applications','study_members','study_days',
        'profile_days','refresh_sessions','refresh_tokens','flyway_schema_history']


def utc(): return datetime.datetime.now(datetime.timezone.utc).isoformat()

def run(args, stdin=None):
    return subprocess.run(args,input=stdin,check=True,capture_output=True,text=True).stdout.strip()

def inspect(cid): return json.loads(run(['docker','inspect',cid]))[0]

def identity(cid):
    item=inspect(cid)
    return {'id':item['Id'],'startedAt':item['State']['StartedAt'],'restartCount':item['RestartCount']}

def sql(query):
    # Only static SQL and generated numeric/hex identifiers are passed on stdin.
    return run(COMPOSE+['exec','-T','postgres','sh','-c',
                       'exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],query)

def fingerprints():
    selects=[f"SELECT json_build_object('table','{table}','rows',count(*),'contentMd5',coalesce(md5(string_agg(row_to_json(t)::text, '' ORDER BY row_to_json(t)::text)),'')) FROM {table} t" for table in TABLES]
    lines=sql('BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;\n'+'\nUNION ALL\n'.join(selects)+';\nCOMMIT;').splitlines()
    result={}
    for line in lines:
        value=json.loads(line);result[value.pop('table')]=value
    assert set(result)==set(TABLES)
    return result

def token_state(raw):
    digest=hashlib.sha256(raw.encode()).hexdigest()
    result=sql("SELECT json_build_object('sessionId',s.id,'expiresAt',s.expires_at,'revoked',s.revoked_at IS NOT NULL,'consumed',t.consumed_at IS NOT NULL) FROM refresh_tokens t JOIN refresh_sessions s ON s.id=t.session_id WHERE t.token_hash='"+digest+"';")
    assert result,'Token hash missing from database'
    return json.loads(result)

def api(path,method='GET',body=None,token=None):
    headers={'Content-Type':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    req=urllib.request.Request('http://localhost:18082'+path,method=method,headers=headers,
                               data=None if body is None else json.dumps(body).encode())
    try: response=urllib.request.urlopen(req,timeout=12)
    except urllib.error.HTTPError as error: response=error
    with response:
        raw=response.read()
        return response.status,json.loads(raw) if raw else {},response.headers.get('X-Request-ID')

def healthy():
    try:
        status,body,_=api('/actuator/health')
        return status==200 and body.get('status')=='UP'
    except (OSError,http.client.HTTPException): return False

def wait_for(check,timeout=180):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        if check(): return
        time.sleep(2)
    raise AssertionError('Backend did not become healthy before timeout')

def save(report):
    REPORT.parent.mkdir(parents=True,exist_ok=True)
    temporary=REPORT.with_suffix('.tmp')
    temporary.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    temporary.replace(REPORT)

def main():
    report={'status':'running','startedAt':utc(),'environment':'isolated local study-monitoring; NOT cloud production','phases':[]}
    save(report)
    def phase(name,**evidence):
        report['phases'].append(dict(name=name,at=utc(),**evidence));save(report);print(name,flush=True)
    try:
        backend=run(COMPOSE+['ps','-q','backend']);postgres=run(COMPOSE+['ps','-q','postgres'])
        assert backend and postgres,'Start scripts/prepare-monitoring.py first'
        for cid,service in [(backend,'backend'),(postgres,'postgres')]:
            info=inspect(cid)
            assert info['Config']['Labels']['com.docker.compose.project']=='study-monitoring'
            assert info['Config']['Labels']['com.docker.compose.service']==service
            assert info['State']['Running']
        assert 'POSTGRES_DB=study_monitoring' in inspect(postgres)['Config']['Env']
        assert healthy()
        old_backend=identity(backend);old_postgres=identity(postgres)
        user='restart-'+secrets.token_hex(6);password=secrets.token_urlsafe(20)
        assert api('/api/auth/register','POST',{'username':user,'password':password,'email':user+'@test.com'})[0]==201
        status,first,_=api('/api/auth/login','POST',{'username':user,'password':password})
        assert status==200 and first['accessToken'] and first['refreshToken']
        original_session=token_state(first['refreshToken'])
        assert not original_session['consumed'] and not original_session['revoked']
        phase('logged_in',httpStatus=200,refreshTokenIssued=True,refreshTokenHashPersisted=True)
        assert api('/api/profiles/me','PUT',{'department':'Restart proof','category':'PROGRAMMING','level':'BEGINNER',
                    'days':['MONDAY'],'startTime':'19:00','endTime':'20:00'},first['accessToken'])[0]==200
        status,study,_=api('/api/studies','POST',{'title':'Backend restart proof','category':'PROGRAMMING',
                    'level':'BEGINNER','days':['MONDAY'],'startTime':'19:00','endTime':'20:00','maxMembers':3},first['accessToken'])
        assert status==201
        study_path='/api/studies/'+str(int(study['id']))
        existing={path:api(path,token=first['accessToken'])[1] for path in ['/api/members/me','/api/profiles/me',study_path]}
        before=fingerprints()
        phase('database_baseline_recorded',fingerprints=before)
        try:
            run(['docker','stop','--time','45',backend])
            assert not inspect(backend)['State']['Running']
            try:
                api('/actuator/health')
            except (OSError,http.client.HTTPException):
                phase('backend_stopped',apiReachable=False,postgresStillRunning=inspect(postgres)['State']['Running'])
            else:
                raise AssertionError('Backend endpoint remained reachable while stopped')
            # The database continues to hold the same rows while the app is absent.
            assert fingerprints()==before
        finally:
            run(['docker','start',backend])
            wait_for(healthy)
            phase('backend_restarted',healthHttpStatus=200,health='UP')
        new_backend=identity(backend)
        assert new_backend['id']==old_backend['id'] and new_backend['startedAt']!=old_backend['startedAt']
        assert identity(postgres)==old_postgres,'PostgreSQL must not restart'
        after=fingerprints()
        assert after==before,'Database rows changed across backend restart'
        assert token_state(first['refreshToken'])==original_session
        for path,previous in existing.items():
            status,current,_=api(path,token=first['accessToken'])
            assert status==200 and current==previous,'Existing API data changed'
        phase('database_preserved',tablesCompared=len(TABLES),allRowCountsAndHashesMatch=True,
              existingApiDataMatches=True,postgresIdentityUnchanged=True,refreshSessionUnchanged=True)
        status,second,request_id=api('/api/auth/refresh','POST',{'refreshToken':first['refreshToken']})
        assert status==200
        assert second['refreshToken']!=first['refreshToken'] and second['accessToken']!=first['accessToken']
        next_session=token_state(second['refreshToken'])
        assert next_session['sessionId']==original_session['sessionId']
        assert next_session['expiresAt']==original_session['expiresAt']
        assert not next_session['consumed'] and not next_session['revoked']
        assert token_state(first['refreshToken'])['consumed']
        status,member,_=api('/api/members/me',token=second['accessToken'])
        assert status==200 and member==existing['/api/members/me']
        phase('original_refresh_token_rotated',httpStatus=200,requestId=request_id,newTokenPairIssued=True,
              oldRefreshConsumed=True,sameSession=True,absoluteExpiryPreserved=True,newAccessTokenWorks=True)
        assert api('/api/auth/logout','POST',{'refreshToken':second['refreshToken']})[0]==204
        phase('verification_session_logged_out',httpStatus=204)
        report.update(status='passed',completedAt=utc(),backendBefore=old_backend,backendAfter=new_backend,
                      postgresUnchanged=old_postgres,backendImage=inspect(backend)['Image'],
                      tokenValuesSaved=False,applicationCodeChanged=False)
        save(report)
        print('Backend restart scenario passed. Evidence: '+str(REPORT),flush=True)
    except BaseException as error:
        report.update(status='failed',failureType=type(error).__name__,completedAt=utc())
        save(report)
        raise

if __name__=='__main__':
    def interrupt(signum,frame): raise KeyboardInterrupt('Interrupted; restoring backend')
    signal.signal(signal.SIGTERM,interrupt)
    main()
