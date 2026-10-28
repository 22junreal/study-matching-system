#!/usr/bin/env python3
"""SIGKILL only study-monitoring/postgres; always attempt recovery, never restart the app."""
import base64
from concurrent.futures import ThreadPoolExecutor
import datetime
import json
import math
from pathlib import Path
import secrets
import signal
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
STATE = Path('/private/tmp' if Path('/private/tmp').exists() else tempfile.gettempdir())/'study-monitoring'
COMPOSE = ['docker','compose','--env-file',str(STATE/'environment'),'-p','study-monitoring',
           '-f',str(ROOT/'infra/compose.prod.yml'),'-f',str(ROOT/'infra/compose.monitoring.yml'),
           '-f',str(ROOT/'infra/compose.edge.yml')]
BACKEND='http://localhost:18082'
REPORT=ROOT/'reports/verification/postgres-recovery.json'
REPORT.parent.mkdir(parents=True,exist_ok=True)

def command(args):
    return subprocess.check_output(args,text=True,stderr=subprocess.STDOUT).strip()

def utc(): return datetime.datetime.now(datetime.timezone.utc).isoformat()

def request(url, data=None, auth=None):
    headers={'Content-Type':'application/json'}
    if auth: headers['Authorization']=auth
    req=urllib.request.Request(url,data=None if data is None else json.dumps(data).encode(),headers=headers)
    started=time.monotonic()
    try:
        with urllib.request.urlopen(req,timeout=12) as response:
            raw=response.read()
            return response.status,json.loads(raw) if raw else {},response.headers.get('X-Request-ID'),round(time.monotonic()-started,3)
    except urllib.error.HTTPError as error:
        return error.code,json.loads(error.read()),error.headers.get('X-Request-ID'),round(time.monotonic()-started,3)

def prom(expr, grafana=False):
    base=('http://localhost:13000/api/datasources/proxy/uid/prometheus/api/v1/query' if grafana else 'http://localhost:19090/api/v1/query')
    status,body,_,_=request(base+'?'+urllib.parse.urlencode({'query':expr}),auth=grafana_auth if grafana else None)
    assert status==200 and body['status']=='success','Metrics query failed'
    return body['data']['result']

def value(expr,grafana=False):
    rows=prom(expr,grafana)
    return float(rows[0]['value'][1]) if rows else math.nan

def wait_for(check,description,timeout=150):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        if check(): return
        time.sleep(3)
    raise AssertionError('Timed out: '+description)

def identity(cid):
    d=json.loads(command(['docker','inspect',cid]))[0]
    return {'id':d['Id'],'startedAt':d['State']['StartedAt'],'restartCount':d['RestartCount']}

queries={
 'scrapeUp':'up{job="study-backend"}',
 'databaseAvailable':'study_database_available{job="study-backend"}',
 'poolTotal':'hikaricp_connections{job="study-backend"}',
 'poolPending':'hikaricp_connections_pending{job="study-backend"}',
 'poolTimeouts':'hikaricp_connections_timeout_total{job="study-backend"}',
 'apiErrorRatio':'(sum(rate(http_server_requests_seconds_count{job="study-backend",uri!~"/actuator.*",status=~"5.."}[1m])) or vector(0)) / clamp_min(sum(rate(http_server_requests_seconds_count{job="study-backend",uri!~"/actuator.*"}[1m])),0.001)',
}

def snapshot(grafana=False):
    result={key:value(expr,grafana) for key,expr in queries.items()}
    assert all(math.isfinite(v) for v in result.values()),'Metrics need multiple scrapes before verification'
    return result

report={'status':'running','startedAt':utc(),'environment':'isolated local study-monitoring Docker Compose; NOT cloud production','phases':[]}

def phase(name,**evidence):
    report['phases'].append(dict(name=name,at=utc(),**evidence))
    print(name,flush=True)

# Guard the destructive step against accidental selection of the development DB.
pg=command(COMPOSE+['ps','-q','postgres'])
backend_id=command(COMPOSE+['ps','-q','backend'])
assert pg and backend_id,'Start scripts/prepare-monitoring.py first'
pg_info=json.loads(command(['docker','inspect',pg]))[0]
assert pg_info['Config']['Labels']['com.docker.compose.project']=='study-monitoring'
assert pg_info['Config']['Labels']['com.docker.compose.service']=='postgres'
assert 'POSTGRES_DB=study_monitoring' in pg_info['Config']['Env']
original_policy=pg_info['HostConfig']['RestartPolicy']
original_backend=identity(backend_id)
grafana_password=(STATE/'secrets/grafana.password').read_text().strip()
grafana_auth='Basic '+base64.b64encode(('admin:'+grafana_password).encode()).decode()
stop=threading.Event()
lock=threading.Lock()
responses=[]
worker_errors=[]
executor=None
changed_restart_policy=False
outage_started=None

def interrupt(signum,frame): raise KeyboardInterrupt('Interrupted; restoring isolated PostgreSQL')
signal.signal(signal.SIGTERM,interrupt)

def traffic():
    while not stop.is_set():
        try:
            status,body,request_id,duration=request(BACKEND+'/api/studies',auth=auth)
            with lock:
                responses.append({'status':status,'requestId':request_id,'durationSeconds':duration,
                                  'code':body.get('code'),'at':utc()})
        except Exception as error:
            with lock: worker_errors.append(type(error).__name__)
        stop.wait(1)

try:
    user='db-proof-'+secrets.token_hex(6)
    password=secrets.token_urlsafe(20)
    assert request(BACKEND+'/api/auth/register',{'username':user,'email':user+'@test.com','password':password})[0]==201
    status,tokens,_,_=request(BACKEND+'/api/auth/login',{'username':user,'password':password})
    assert status==200
    auth='Bearer '+tokens['accessToken']
    warm_started=time.monotonic()
    def warm():
        assert request(BACKEND+'/api/studies',auth=auth)[0]==200
        return time.monotonic()-warm_started>=35 and value(queries['databaseAvailable'])==1
    wait_for(warm,'healthy traffic and database metrics')
    baseline=snapshot(grafana=True)
    assert baseline['scrapeUp']==baseline['databaseAvailable']==1 and baseline['poolTotal']>0
    health=request(BACKEND+'/actuator/health')
    assert health[0]==200 and health[1]['status']=='UP'
    process_start=value('process_start_time_seconds{job="study-backend"}')
    assert math.isfinite(process_start)
    phase('normal',apiStatus=200,healthStatus=200,health='UP',grafanaMetrics=baseline)

    # Docker restart policy is disabled temporarily so SIGKILL really creates an outage.
    command(['docker','update','--restart=no',pg]);changed_restart_policy=True
    outage_started=time.monotonic()
    command(['docker','kill','--signal=KILL',pg])
    killed=json.loads(command(['docker','inspect',pg]))[0]['State']
    assert not killed['Running'] and killed['ExitCode']==137
    phase('postgres_killed',signal='SIGKILL',exitCode=137)
    executor=ThreadPoolExecutor(max_workers=4)
    workers=[executor.submit(traffic) for _ in range(4)]
    wait_for(lambda:any(r['status']==500 and r['code']=='INTERNAL_SERVER_ERROR' for r in responses),'API returns 500, not 401')
    with lock:
        first_failure=next(dict(r) for r in responses if r['status']==500)
    phase('api_failed',httpStatus=500,code=first_failure['code'],requestId=first_failure['requestId'])
    health=request(BACKEND+'/actuator/health')
    assert health[0]==503 and health[1]['status']=='DOWN'
    phase('health_down',httpStatus=health[0],health=health[1]['status'],durationSeconds=health[3])
    wait_for(lambda:value(queries['databaseAvailable'])==0,'database connectivity gauge down')
    wait_for(lambda:value('ALERTS{alertname="StudyDatabaseUnavailable",alertstate="firing"}')==1,'database alert firing')
    wait_for(lambda:value(queries['apiErrorRatio'])>0,'API error ratio rises')
    wait_for(lambda:value(queries['poolTimeouts'])>baseline['poolTimeouts'],'pool acquisition timeouts increase')
    down=snapshot(grafana=True)
    assert down['scrapeUp']==1 and down['databaseAvailable']==0
    assert down['poolTotal']<baseline['poolTotal']
    pending_max=value('max_over_time(hikaricp_connections_pending{job="study-backend"}[2m])',grafana=True)
    assert pending_max>0
    with lock:
        failures=[dict(r) for r in responses if r['status']==500]
        assert not any(r['status']==401 for r in responses)
        assert not worker_errors,worker_errors
    phase('database_alert_firing',secondsAfterKill=round(time.monotonic()-outage_started,1),
          failedRequests=len(failures),grafanaMetrics=down,poolPendingMaximum=pending_max)

    logs=command(COMPOSE+['logs','--no-color','--no-log-prefix','--since',report['startedAt'],'backend'])
    failed_ids={r['requestId'] for r in failures}
    evidence=[]
    for line in logs.splitlines():
        try: event=json.loads(line)
        except json.JSONDecodeError: continue
        text=json.dumps(event)
        if event.get('requestId') in failed_ids and any(marker in text for marker in ['SQLTransientConnectionException','PSQLException','JDBCConnectionException']):
            evidence.append({'timestamp':event.get('@timestamp'),'requestId':event['requestId'],
                             'logger':event.get('log',{}).get('logger'),'hasDatabaseExceptionStack':True})
    assert evidence,'Expected requestId-correlated database exception logs'
    assert 'database_connectivity state=DOWN' in logs
    phase('database_errors_logged',matchingEvents=len(evidence),samples=evidence[:3])
finally:
    # Restore DB before waiting for blocked HTTP workers. This also runs on assertions/signals.
    stop.set()
    if changed_restart_policy:
        try:
            command(['docker','start',pg])
            policy=original_policy['Name']
            if policy=='on-failure' and original_policy.get('MaximumRetryCount'):
                policy+=':'+str(original_policy['MaximumRetryCount'])
            command(['docker','update','--restart='+policy,pg])
            wait_for(lambda:command(['docker','inspect','--format','{{.State.Health.Status}}',pg])=='healthy', 'PostgreSQL healthy after restart',timeout=120)
            phase('postgres_restarted',originalRestartPolicyRestored=policy)
        except Exception as recovery_error:
            report['recoveryErrorType']=type(recovery_error).__name__
            report['status']='failed'
            REPORT.write_text(json.dumps(report,indent=2)+'\n')
            raise
    if executor: executor.shutdown(wait=True)
    # A failure must not leave an old success report looking current.
    report['status']='verification_incomplete'
    REPORT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')

# Reached only if every outage assertion above succeeded.
recovery_started=time.monotonic()
wait_for(lambda:request(BACKEND+'/actuator/health')[0]==200,'health UP after PostgreSQL restart')
assert request(BACKEND+'/actuator/health')[1]['status']=='UP'
wait_for(lambda:request(BACKEND+'/api/studies',auth=auth)[0]==200,'API recovery')
wait_for(lambda:value(queries['databaseAvailable'])==1 and value(queries['poolTotal'])>0,'Hikari reconnection')
wait_for(lambda:not prom('ALERTS{alertname="StudyDatabaseUnavailable",alertstate="firing"}'),'database alert resolved')
assert identity(backend_id)==original_backend,'Backend must not restart during DB recovery'
assert value('process_start_time_seconds{job="study-backend"}')==process_start
phase('recovered',apiStatus=200,healthStatus=200,health='UP',backendRestarted=False,
      databaseAlertResolved=True,secondsSincePostgresHealthy=round(time.monotonic()-recovery_started,1),
      grafanaMetrics=snapshot(grafana=True))
# The 1-minute error-rate window needs fresh successful requests to clear historical errors.
window_start=time.monotonic()
def errors_cleared():
    assert request(BACKEND+'/api/studies',auth=auth)[0]==200
    ratio=value(queries['apiErrorRatio'])
    return time.monotonic()-window_start>=65 and math.isfinite(ratio) and ratio==0
wait_for(errors_cleared,'rolling error ratio returns to zero',timeout=130)
assert request(BACKEND+'/api/auth/logout',{'refreshToken':tokens['refreshToken']})[0]==204
assert not prom('ALERTS{alertname="StudyDatabaseUnavailable",alertstate="firing"}')
assert value(queries['poolPending'])==0
phase('normal_again',grafanaMetrics=snapshot(grafana=True),databaseAlertResolved=True)
report.update(status='passed',completedAt=utc(),backendIdentityUnchanged=True,
              originalBackend=original_backend,backendImage=command(['docker','inspect','--format','{{.Image}}',backend_id]),
              grafanaUrl='http://localhost:13000/d/study-backend?'+urllib.parse.urlencode({
                  'from':int(datetime.datetime.fromisoformat(report['startedAt']).timestamp()*1000)-30000,
                  'to':int(time.time()*1000)+15000}),externalNotificationsSent=False)
REPORT.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('PostgreSQL recovery scenario passed. Evidence: '+str(REPORT),flush=True)
