#!/usr/bin/env python3
"""Local evidence only: requires the isolated study-proof stack and its local CA."""
import concurrent.futures, datetime, hashlib, json, pathlib, secrets, ssl, subprocess, time, urllib.request, urllib.error
ROOT = pathlib.Path(__file__).resolve().parents[1]
ENV = '/private/tmp/study-proof.env'
PROJECT = 'study-proof'
CA = '/private/tmp/study-proof-ca.crt'
REPORT = ROOT / 'reports/verification/operations.json'
COMPOSE = ['docker','compose','--env-file',ENV,'-p',PROJECT,'-f',str(ROOT/'infra/compose.prod.yml'),'-f',str(ROOT/'infra/compose.edge.yml')]
CTX = ssl.create_default_context(cafile=CA)
def run(args, **kwargs):
    return subprocess.run(args, check=True, capture_output=True, text=True, **kwargs).stdout

def api(path, method='GET', body=None, token=None, secure=True, extra=None):
    headers = {'Content-Type':'application/json', **(extra or {})}
    if token: headers['Authorization'] = 'Bearer ' + token
    request = urllib.request.Request(('https://localhost:18443' if secure else 'http://localhost:18081')+path,
            data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try:
        response = urllib.request.urlopen(request, context=CTX, timeout=45)
    except urllib.error.HTTPError as e:
        response = e
    content = response.read()
    return response.status, dict(response.headers), json.loads(content) if content else None

def sql(query, database='study_proof'):
    return run(COMPOSE + ['exec','-T','postgres','psql','-X','-v','ON_ERROR_STOP=1','-U','proof_user','-d',database,'-At','-c',query]).strip()

def fingerprint(database):
    tables = ['members','profiles','studies','study_applications','study_members','study_days','profile_days','flyway_schema_history']
    return {table:sql(f"SELECT count(*) || ':' || coalesce(md5(string_agg(row_to_json(t)::text, '' ORDER BY row_to_json(t)::text)), '') FROM {table} t",database) for table in tables}

def main():
    report = {'checkedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(), 'environment':'local Docker Desktop; NOT cloud production', 'tls':'Caddy local CA, certificate verification enabled (no insecure bypass)'}
    status, headers, body = api('/actuator/health')
    assert status == 200 and body['status'] == 'UP'
    report['httpsHealth'] = {'status':status, 'hsts':headers.get('Strict-Transport-Security'), 'requestId':headers.get('X-Request-Id') or headers.get('X-Request-ID')}
    suffix = secrets.token_hex(4)
    password = secrets.token_urlsafe(20)
    username = 'proof_' + suffix
    assert api('/api/auth/register','POST',{'username':username,'email':username+'@example.test','password':password},secure=False)[0] == 201
    status, _, login = api('/api/auth/login','POST',{'username':username,'password':password})
    assert status == 200
    token = login['accessToken']
    status, _, created = api('/api/studies','POST',{'title':'operations proof','category':' ai ','level':'beginner','days':['wed','MON'],'startTime':'19:00','endTime':'21:00','maxMembers':3},token,secure=False)
    assert status == 201 and created['days'] == ['MONDAY','WEDNESDAY']
    assert api('/api/studies/'+str(created['id']),token=token)[0] == 200
    report['authenticatedHttpsApi'] = True
    assert api('/api/profiles/me','PUT',{'department':'Computer Science','category':'AI','level':'BEGINNER',
            'days':['MONDAY','WEDNESDAY'],'startTime':'19:00','endTime':'21:00'},token,secure=False)[0] == 200
    applicant = 'applicant_' + suffix
    assert api('/api/auth/register','POST',{'username':applicant,'email':applicant+'@example.test','password':password},secure=False)[0] == 201
    applicant_token = api('/api/auth/login','POST',{'username':applicant,'password':password},secure=False)[2]['accessToken']
    application = api('/api/studies/'+str(created['id'])+'/applications','POST',token=applicant_token,secure=False)
    assert application[0] == 201
    assert api('/api/studies/'+str(created['id'])+'/applications/'+str(application[2]['id'])+'/approve','PATCH',token=token,secure=False)[0] == 200
    report['schemaVersion'] = sql('SELECT max(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL')
    def limited(i):
        # Spoofing this header must not bypass Caddy's overwrite/rate limiting.
        return api('/api/auth/login','POST',{'username':username,'password':'incorrect'},extra={'X-Study-Client-IP':f'203.0.113.{i+1}'})
    with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:
        results = list(pool.map(limited,range(12)))
    counts = {str(s):sum(r[0] == s for r in results) for s in {r[0] for r in results}}
    limited_responses = [r for r in results if r[0] == 429]
    assert limited_responses and all(r[2]['code']=='RATE_LIMITED' and r[1].get('Retry-After')=='12' for r in limited_responses)
    assert all(r[0] in (401,429) for r in results)
    report['rateLimit'] = {'requestCount':12, 'statuses':counts, 'forgedClientIpDidNotBypass':True, 'retryAfterSeconds':12}
    before = fingerprint('study_proof')
    dump = pathlib.Path('/private/tmp/study-proof-'+suffix+'.dump')
    start = time.monotonic()
    run(['bash',str(ROOT/'scripts/backup-db.sh'),ENV,PROJECT,str(dump)])
    backup_seconds = time.monotonic()-start
    restored = 'restore_' + suffix
    start = time.monotonic()
    run(['bash',str(ROOT/'scripts/restore-db.sh'),ENV,PROJECT,str(dump),restored])
    assert fingerprint(restored) == before
    report['backupRestore'] = {'sameRowCountsAndContentHashes':True,'fingerprints':before,'dumpBytes':dump.stat().st_size,'sha256':hashlib.sha256(dump.read_bytes()).hexdigest(),'backupSeconds':round(backup_seconds,3),'restoreAndVerifySeconds':round(time.monotonic()-start,3),'target':'new isolated database; source unchanged'}
    run(COMPOSE+['restart','backend'])
    deadline=time.monotonic()+90
    while True:
        try:
            if api('/actuator/health')[0] == 200: break
        except Exception: pass
        if time.monotonic()>deadline: raise RuntimeError('Backend did not recover')
        time.sleep(1)
    assert api('/api/studies/'+str(created['id']),token=token,secure=False)[0] == 200
    assert fingerprint('study_proof') == before
    report['restartPersistence'] = True
    try:
        run(COMPOSE+['stop','postgres'])
        status, _, health = api('/actuator/health')
        assert status == 503 and health['status'] == 'DOWN'
        report['databaseOutageStatus'] = status
    finally:
        run(COMPOSE+['up','-d','--wait','postgres'])
    deadline=time.monotonic()+90
    while True:
        try:
            if api('/actuator/health')[0] == 200: break
        except Exception: pass
        if time.monotonic()>deadline: raise RuntimeError('Database recovery did not restore health')
        time.sleep(1)
    assert api('/api/studies/'+str(created['id']),token=token,secure=False)[0] == 200
    report['databaseRecoveryWithoutAppRestart'] = True
    marker = 'secret-query-'+suffix
    status, headers, _ = api('/actuator/health?private='+marker)
    assert status == 200
    request_id = headers.get('X-Request-Id') or headers.get('X-Request-ID')
    time.sleep(0.2)
    backend_logs=run(COMPOSE+['logs','--no-log-prefix','backend'])
    gateway_logs=run(COMPOSE+['logs','--no-log-prefix','gateway'])
    assert password not in backend_logs+gateway_logs and token not in backend_logs+gateway_logs and marker not in backend_logs+gateway_logs
    records=[]
    for line in backend_logs.splitlines():
        try: records.append(json.loads(line))
        except ValueError: pass
    record=next(r for r in records if r.get('requestId')==request_id)
    assert request_id in gateway_logs
    report['logging'] = {'structuredBackendRecord':record,'edgeAndAppRequestIdCorrelated':True,'passwordTokenQueryMarkerAbsent':True}
    report['backendImageId'] = run(['docker','inspect','--format','{{.Image}}','study-proof-backend-1']).strip()
    report['cloudDeployed'] = False
    REPORT.parent.mkdir(parents=True, exist_ok=True)
    REPORT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    # Only the temporary restore database and temporary dump are removed.
    sql('DROP DATABASE '+restored)
    dump.unlink()
    print(json.dumps({k:report[k] for k in ['environment','schemaVersion','rateLimit','restartPersistence','cloudDeployed']},ensure_ascii=False))
    print('Saved reports/verification/operations.json')
if __name__=='__main__': main()
