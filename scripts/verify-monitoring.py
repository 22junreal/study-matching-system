#!/usr/bin/env python3
"""Verify only the isolated study-monitoring stack, including a reversible backend outage."""
import base64
import datetime
import json
import math
from pathlib import Path
import secrets
import ssl
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
STATE = Path('/private/tmp' if Path('/private/tmp').exists() else tempfile.gettempdir()) / 'study-monitoring'
COMPOSE = ['docker', 'compose', '--env-file', str(STATE/'environment'), '-p', 'study-monitoring',
           '-f', str(ROOT/'infra/compose.prod.yml'), '-f', str(ROOT/'infra/compose.monitoring.yml'),
           '-f', str(ROOT/'infra/compose.edge.yml')]
context = ssl.create_default_context(cafile=str(STATE/'ca.crt'))

def call(url, data=None, authorization=None):
    headers = {'Content-Type': 'application/json'}
    if authorization:
        headers['Authorization'] = authorization
    request = urllib.request.Request(url, data=None if data is None else json.dumps(data).encode(), headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=10, context=context) as response:
            return response.status, response.read().decode()
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode()

def basic(user, password):
    return 'Basic ' + base64.b64encode((user+':'+password).encode()).decode()

def query(expr, grafana=False):
    url = ('http://localhost:13000/api/datasources/proxy/uid/prometheus/api/v1/query' if grafana
           else 'http://localhost:19090/api/v1/query') + '?' + urllib.parse.urlencode({'query':expr})
    status, raw = call(url, authorization=grafana_auth if grafana else None)
    assert status == 200, ('Prometheus query HTTP status',status)
    result=json.loads(raw)
    assert result['status']=='success', 'Prometheus query failed'
    return result['data']['result']

def wait_for(check, description, timeout=150):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        if check(): return
        time.sleep(3)
    raise AssertionError('Timed out: '+description)

def first_value(expr):
    rows=query(expr)
    return rows[0]['value'][1] if rows else None

metrics_password=(STATE/'secrets/monitoring.password').read_text().strip()
grafana_auth=basic('admin',(STATE/'secrets/grafana.password').read_text().strip())
metrics_auth=basic('prometheus',metrics_password)
backend='http://localhost:18082'
assert call(backend+'/actuator/prometheus')[0]==401
assert call(backend+'/actuator/prometheus',authorization=basic('prometheus','incorrect'))[0]==401
status, raw=call(backend+'/actuator/prometheus',authorization=metrics_auth)
assert status==200 and 'jvm_memory_used_bytes' in raw and metrics_password not in raw
assert call('https://localhost:18444/actuator/prometheus',authorization=metrics_auth)[0]==404
assert call(backend+'/api/members/me',authorization=metrics_auth)[0]==401
user='monitor-'+secrets.token_hex(6)
password=secrets.token_urlsafe(18)
assert call(backend+'/api/auth/register',{'username':user,'email':user+'@test.com','password':password})[0]==201
status,raw=call(backend+'/api/auth/login',{'username':user,'password':password})
assert status==200
tokens=json.loads(raw)
auth='Bearer '+tokens['accessToken']
assert call(backend+'/actuator/prometheus',authorization=auth)[0]==401
latency_query = 'histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{job="study-backend",uri="/api/studies"}[1m])))'

def warm_api_metrics():
    # A histogram's existence alone is insufficient: rate needs multiple scrapes.
    started = time.monotonic()
    def ready():
        assert call(backend+'/api/studies',authorization=auth)[0]==200
        value=first_value(latency_query)
        return (time.monotonic()-started >= 30 and value is not None
                and math.isfinite(float(value)) and float(value)>0)
    wait_for(ready, 'finite positive API p95 after multiple scrapes', timeout=120)

warm_api_metrics()
wait_for(lambda:first_value('up{job="study-backend"}')=='1','backend scrape up')
wait_for(lambda:bool(query('http_server_requests_seconds_bucket{job="study-backend",uri="/api/studies"}')),'HTTP histogram')
status,raw=call('http://localhost:13000/api/dashboards/uid/study-backend',authorization=grafana_auth)
assert status==200
panels=json.loads(raw)['dashboard']['panels']
status,raw=call('http://localhost:13000/api/datasources/uid/prometheus/health',authorization=grafana_auth)
assert status==200 and json.loads(raw)['status']=='OK'
assert query('up{job="study-backend"}',grafana=True)[0]['value'][1]=='1'
queries=[]
for panel in panels:
    for target in panel['targets']:
        result=query(target['expr'].replace('$__rate_interval','1m'))
        queries.append({'panel':panel['title'],'expression':target['expr'],'seriesCount':len(result)})
assert query('hikaricp_connections_max{job="study-backend"}')
assert query('jvm_memory_used_bytes{job="study-backend"}')
for result in queries:
    if result['panel'] != 'Prometheus alerts':
        assert result['seriesCount'] > 0, ('Missing dashboard metric', result['panel'])
# Credentials stay outside saved evidence. A synthetic account's refresh session is ended.
assert call(backend+'/api/auth/logout',{'refreshToken':tokens['refreshToken']})[0]==204
report={'checkedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),
        'environment':'isolated local Docker Compose, NOT cloud production',
        'backendScrapeUp':True,'anonymousMetricsStatus':401,'wrongPasswordStatus':401,
        'businessJwtMetricsStatus':401,'metricsCredentialsBusinessApiStatus':401,'edgeMetricsStatus':404,
        'grafanaDatasourceHealthy':True,'grafanaProxyQuerySucceeded':True,'dashboardUid':'study-backend',
        'dashboardPanelCount':len(panels),'queries':queries,'externalNotificationsConfigured':False,
        'distributedTracingConfigured':False}
print('Verified authenticated scrape, Grafana datasource, dashboard queries and edge protection.',flush=True)
started=time.monotonic()
try:
    subprocess.run(COMPOSE+['stop','backend'],check=True,stdout=subprocess.DEVNULL)
    wait_for(lambda:first_value('up{job="study-backend"}')=='0','scrape failure')
    wait_for(lambda:first_value('ALERTS{alertname="StudyBackendDown",alertstate="firing"}')=='1','backend down alert firing')
    report['backendDownAlertFired']=True
    report['secondsUntilDownAlert']=round(time.monotonic()-started,1)
    print('Backend-down alert fired; restoring backend.',flush=True)
finally:
    subprocess.run(COMPOSE+['up','-d','--wait','--wait-timeout','180','backend'],check=True,stdout=subprocess.DEVNULL)
wait_for(lambda:first_value('up{job="study-backend"}')=='1','scrape recovery')
wait_for(lambda:not query('ALERTS{alertname="StudyBackendDown",alertstate="firing"}'),'alert resolved')
warm_api_metrics()
report['apiP95SecondsAfterRecovery']=float(first_value(latency_query))
report['backendRecovered']=True
report['backendDownAlertResolved']=True
report['images']={}
for service in ['backend','prometheus','grafana']:
    cid=subprocess.check_output(COMPOSE+['ps','-q',service],text=True).strip()
    report['images'][service]=subprocess.check_output(['docker','inspect','--format','{{.Image}}',cid],text=True).strip()
path=ROOT/'reports/verification/monitoring.json'
path.parent.mkdir(parents=True,exist_ok=True)
path.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('Verified backend recovery and alert resolution. Evidence:',path,flush=True)
