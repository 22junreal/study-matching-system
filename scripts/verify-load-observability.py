#!/usr/bin/env python3
"""Measure unmodified API load and pool pressure in the isolated local monitoring stack."""
import base64
import datetime
import json
import math
import os
from pathlib import Path
import secrets
import statistics
import subprocess
import tempfile
import time
import urllib.parse
import urllib.request

ROOT=Path(__file__).resolve().parents[1]
STATE=Path('/private/tmp' if Path('/private/tmp').exists() else tempfile.gettempdir())/'study-monitoring'
COMPOSE=['docker','compose','--env-file',str(STATE/'environment'),'-p','study-monitoring',
         '-f',str(ROOT/'infra/compose.prod.yml'),'-f',str(ROOT/'infra/compose.monitoring.yml'),'-f',str(ROOT/'infra/compose.edge.yml')]
RUN_ID='load-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')+'-'+secrets.token_hex(3)
OUT=ROOT/'reports/load-observability'/RUN_ID
OUT.mkdir(parents=True,exist_ok=False)
PLAN=[('baseline',1,0,45),('medium',10,45,45),('high',30,90,60),('peak',60,150,60),('recovery',1,210,90)]

def run(args,stdin=None):
    return subprocess.run(args,input=stdin,check=True,capture_output=True,text=True).stdout.strip()

def api(path,body=None,token=None):
    headers={'Content-Type':'application/json'}
    if token:headers['Authorization']='Bearer '+token
    request=urllib.request.Request('http://localhost:18082'+path,data=None if body is None else json.dumps(body).encode(),headers=headers)
    with urllib.request.urlopen(request,timeout=15) as response:
        raw=response.read();return response.status,json.loads(raw) if raw else {}

def sql(query):
    return run(COMPOSE+['exec','-T','postgres','sh','-c','exec psql -X -qAt -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'],query)

def prom(endpoint,params):
    with urllib.request.urlopen('http://localhost:19090/api/v1/'+endpoint+'?'+urllib.parse.urlencode(params),timeout=20) as response:
        result=json.load(response)
    assert result['status']=='success'
    return result['data']['result']

backend=run(COMPOSE+['ps','-q','backend']);postgres=run(COMPOSE+['ps','-q','postgres'])
assert backend and postgres
for cid,service in [(backend,'backend'),(postgres,'postgres')]:
    data=json.loads(run(['docker','inspect',cid]))[0]
    assert data['Config']['Labels']['com.docker.compose.project']=='study-monitoring'
    assert data['Config']['Labels']['com.docker.compose.service']==service
assert sql('SELECT current_database();')=='study_monitoring'
identity=run(['docker','inspect','--format','{{.Id}} {{.State.StartedAt}}',backend])
assert api('/actuator/health')[1]['status']=='UP'
user='load-'+secrets.token_hex(6);password=secrets.token_urlsafe(20)
status,member=api('/api/auth/register',{'username':user,'email':user+'@test.com','password':password});assert status==201
_,tokens=api('/api/auth/login',{'username':user,'password':password})
fixture_count=int(sql("SELECT count(*) FROM studies WHERE title LIKE 'load-observe-fixture-%';"))
assert fixture_count in (0,120000),'Unexpected generated fixture count; inspect before modifying'
if fixture_count==0:
    sql(f"""BEGIN;
INSERT INTO studies(owner_id,title,description,category,level,max_members,status,created_at,updated_at)
SELECT {int(member['id'])},'load-observe-fixture-'||i,repeat('observability fixture ',5),
 'PROGRAMMING','BEGINNER',5,'RECRUITING',TIMESTAMP '2026-01-01'+i*INTERVAL '1 second',TIMESTAMP '2026-01-01'
FROM generate_series(1,120000) i;
INSERT INTO study_members(study_id,member_id,status) SELECT id,owner_id,'ACTIVE' FROM studies WHERE title LIKE 'load-observe-fixture-%';
INSERT INTO study_days(study_id,day) SELECT id,day FROM studies CROSS JOIN (VALUES ('MONDAY'),('WEDNESDAY')) d(day) WHERE title LIKE 'load-observe-fixture-%';
COMMIT; ANALYZE studies; ANALYZE study_days;""")
fixture_count=int(sql("SELECT count(*) FROM studies WHERE title LIKE 'load-observe-fixture-%';"));assert fixture_count==120000
for _ in range(30):
    status,body=api('/api/studies?size=100',token=tokens['accessToken']);assert status==200 and len(body['content'])==100
    time.sleep(0.2)
metadata={'runId':RUN_ID,'status':'running','environment':'local Docker Desktop, isolated study-monitoring; direct API, TLS/rate-limit gateway excluded',
          'fixtureStudies':fixture_count,'pageSize':100,'thinkTimeSeconds':0.05,'k6Image':'grafana/k6:1.3.0',
          'artificialDelayInjected':False,'poolSizeChanged':False,'plan':[dict(phase=p,vus=v,startSeconds=s,durationSeconds=d) for p,v,s,d in PLAN],
          'backendImage':run(['docker','inspect','--format','{{.Image}}',backend]),
          'baseCommit':run(['git','rev-parse','HEAD']),'uncommittedChangesIncluded':True,
          'dockerResources':json.loads(run(['docker','info','--format','{"cpus":{{.NCPU}},"memoryBytes":{{.MemTotal}}}']))}
(OUT/'report.json').write_text(json.dumps(metadata,indent=2)+'\n')
print('Natural load run '+RUN_ID+': 1 -> 10 -> 30 -> 60 -> 1 VUs; 120000 fixture studies.',flush=True)
fd,env_name=tempfile.mkstemp(prefix='k6-load-',dir=STATE)
try:
    with os.fdopen(fd,'w') as stream:
        stream.write('TOKEN='+tokens['accessToken']+'\nBASE_URL=http://backend:18082\nRUN_ID='+RUN_ID+'\n'
                     'K6_PROMETHEUS_RW_SERVER_URL=http://prometheus:9090/api/v1/write\n'
                     'K6_PROMETHEUS_RW_PUSH_INTERVAL=5s\nK6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),avg\n'
                     'K6_PROMETHEUS_RW_STALE_MARKERS=true\n')
    args=['docker','run','--rm','--name',RUN_ID,'--network','study-monitoring_default','--user','0',
          '--env-file',env_name,'-v',str(ROOT/'perf')+':/scripts:ro','-v',str(OUT)+':/results',
          'grafana/k6:1.3.0','run','--out','experimental-prometheus-rw','/scripts/observability.js']
    fallback_start=time.time()
    with (OUT/'console.txt').open('w') as log:
        result=subprocess.run(args,stdout=log,stderr=subprocess.STDOUT,text=True)
    assert result.returncode==0,'k6 validation failed; inspect console.txt and summary.json'
finally:
    Path(env_name).unlink(missing_ok=True)
summary=json.loads((OUT/'summary.json').read_text())
start=datetime.datetime.fromisoformat(summary['setup_data']['startedAt'].replace('Z','+00:00')).timestamp() if summary.get('setup_data',{}).get('startedAt') else fallback_start
end=start+300
assert tokens['accessToken'] not in (OUT/'summary.json').read_text()
selector='job="study-backend",uri="/api/studies"'
queries={
 'vus':'sum(k6_vus{run_id="'+RUN_ID+'"})',
 'k6Rps':'sum(rate(k6_http_reqs_total{run_id="'+RUN_ID+'"}[1m]))',
 'serverP95':f'histogram_quantile(0.95,sum by(le)(rate(http_server_requests_seconds_bucket{{{selector}}}[1m])))',
 'active':'hikaricp_connections_active{job="study-backend"}',
 'maximum':'hikaricp_connections_max{job="study-backend"}',
 'pending':'hikaricp_connections_pending{job="study-backend"}',
 'acquisitionMean':'rate(hikaricp_connections_acquire_seconds_sum{job="study-backend"}[1m])/clamp_min(rate(hikaricp_connections_acquire_seconds_count{job="study-backend"}[1m]),0.001)',
 'processCpu':'process_cpu_usage{job="study-backend"}',
 'connectionHoldMean':'rate(hikaricp_connections_usage_seconds_sum{job="study-backend"}[1m])/clamp_min(rate(hikaricp_connections_usage_seconds_count{job="study-backend"}[1m]),0.001)',
}
# Allow the final samples to be scraped; no high-load traffic remains.
time.sleep(20)
series={name:prom('query_range',{'query':expr,'start':start,'end':end+20,'step':5}) for name,expr in queries.items()}
assert all(series[name] for name in queries),'Expected k6 and application metrics in Prometheus'
(OUT/'prometheus-series.json').write_text(json.dumps({'queries':queries,'start':start,'end':end+20,'stepSeconds':5,'series':series},indent=2)+'\n')
def samples(name,lo,hi):
    return [float(v) for row in series[name] for t,v in row['values'] if lo<=t<=hi and math.isfinite(float(v))]
stages={}
for phase,vus,offset,duration in PLAN:
    # Late samples reduce contamination from the preceding phase in rolling windows.
    lo=start+offset+max(20,duration-30);hi=start+offset+duration-2
    data={name:samples(name,lo,hi) for name in queries}
    assert all(data.values()),'Insufficient phase samples: '+phase
    stats=summary['metrics']['http_req_duration{phase:'+phase+'}']['values']
    stages[phase]={'vus':vus,'clientP95Ms':stats['p(95)'],'clientP99Ms':stats['p(99)'],
                   'serverP95MedianMs':statistics.median(data['serverP95'])*1000,
                   'activeMax':max(data['active']),'activeMedian':statistics.median(data['active']),
                   'poolMaximum':max(data['maximum']),'pendingMax':max(data['pending']),
                   'acquisitionMeanMedianMs':statistics.median(data['acquisitionMean'])*1000,
                   'connectionHoldMeanMedianMs':statistics.median(data['connectionHoldMean'])*1000,
                   'processCpuMax':max(data['processCpu']),'measuredVusMax':max(data['vus'])}
assert run(['docker','inspect','--format','{{.Id}} {{.State.StartedAt}}',backend])==identity,'Backend restarted under load'
assert api('/actuator/health')[1]['status']=='UP'
idle=prom('query',{'query':'hikaricp_connections_active{job="study-backend"}'})
idle_active=float(idle[0]['value'][1]);assert idle_active<=1,'Pool did not return to idle'
assert api('/api/auth/logout',{'refreshToken':tokens['refreshToken']})[0]==204
baseline=stages['baseline'];peak=stages['peak'];recovery=stages['recovery']
pattern=(peak['clientP95Ms']>baseline['clientP95Ms']*1.5 and peak['activeMax']>=peak['poolMaximum']*0.9
         and peak['pendingMax']>0 and recovery['clientP95Ms']<=max(baseline['clientP95Ms']*2,50))
metadata.update(status='passed',startedAt=datetime.datetime.fromtimestamp(start,datetime.timezone.utc).isoformat(),
                completedAt=datetime.datetime.now(datetime.timezone.utc).isoformat(),stages=stages,
                requests=summary['metrics']['http_reqs']['values']['count'],failedRate=summary['metrics']['http_req_failed']['values']['rate'],
                idleActiveConnections=idle_active,backendRestarted=False,requestedPatternObserved=pattern,
                grafanaUrl='http://localhost:13000/d/study-load-correlation?'+urllib.parse.urlencode({'from':int(start*1000)-10000,'to':int((end+20)*1000),'var-run_id':RUN_ID}))
(OUT/'report.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'runId':RUN_ID,'requestedPatternObserved':pattern,'stages':stages,'requests':metadata['requests'],'failedRate':metadata['failedRate'],'grafanaUrl':metadata['grafanaUrl']},indent=2),flush=True)
