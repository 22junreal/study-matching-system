#!/usr/bin/env python3
"""Destructive benchmark fixture changes are restricted to the local study-proof database."""
import datetime, json, os, pathlib, platform, secrets, subprocess, urllib.request, time, sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
OUT=ROOT/'reports/performance'
OUT.mkdir(parents=True,exist_ok=True)
COMPOSE=['docker','compose','--env-file','/private/tmp/study-proof.env','-p','study-proof','-f',str(ROOT/'infra/compose.prod.yml'),'-f',str(ROOT/'infra/compose.edge.yml')]
def run(args, **kwargs): return subprocess.run(args,check=True,capture_output=True,text=True,**kwargs).stdout

def sql(query):
    return run(COMPOSE+['exec','-T','postgres','psql','-X','-v','ON_ERROR_STOP=1','-U','proof_user','-d','study_proof','-At'],input=query).strip()
def api(path, body):
    req=urllib.request.Request('http://localhost:18081'+path,data=json.dumps(body).encode(),headers={'Content-Type':'application/json'})
    with urllib.request.urlopen(req,timeout=30) as r: return json.load(r)

suffix=secrets.token_hex(4)
password=secrets.token_urlsafe(20)
username='bench_'+suffix
owner=api('/api/auth/register',{'username':username,'password':password,'email':username+'@example.test'})
token=api('/api/auth/login',{'username':username,'password':password})['accessToken']
token_file=pathlib.Path('/private/tmp/study-proof-k6.env')
token_file.write_text('TOKEN='+token+'\nBASE_URL=http://backend:18081\n')
os.chmod(token_file,0o600)
assert sql('SELECT current_database();')=='study_proof'
# Only generated fixture rows in the isolated proof database are replaced.
sql("DELETE FROM studies WHERE title LIKE 'bench-fixture-%';")
if '--seed-only' not in sys.argv:
    sql('DROP INDEX IF EXISTS idx_studies_filter_newest; DROP INDEX IF EXISTS idx_studies_newest;')
seed=f"""
INSERT INTO studies(owner_id,title,description,category,level,max_members,status,created_at,updated_at)
SELECT {int(owner['id'])},'bench-fixture-'||i,repeat('benchmark description ',5),
 (ARRAY['PROGRAMMING','AI','DATA','LANGUAGE','CERTIFICATE','ETC'])[1+(i%6)],
 (ARRAY['BEGINNER','INTERMEDIATE','ADVANCED'])[1+((i/6)%3)],
 CASE WHEN (i/18)%2=0 THEN 5 ELSE 1 END,
 CASE WHEN (i/18)%2=0 THEN 'RECRUITING' ELSE 'CLOSED' END,
 TIMESTAMP '2026-01-01' + (i/10)*INTERVAL '1 second',TIMESTAMP '2026-01-01'
FROM generate_series(1,120000) i;
INSERT INTO study_members(study_id,member_id,status) SELECT id,owner_id,'ACTIVE' FROM studies WHERE title LIKE 'bench-fixture-%';
INSERT INTO study_days(study_id,day) SELECT id,day FROM studies CROSS JOIN (VALUES('MONDAY'),('WEDNESDAY')) d(day) WHERE title LIKE 'bench-fixture-%';
ANALYZE studies; ANALYZE members; ANALYZE study_days;
"""
sql(seed)
if '--seed-only' in sys.argv:
    token_file.unlink(missing_ok=True)
    print('Seeded 120000 studies and 240000 day rows in isolated study_proof database.')
    raise SystemExit(0)
queries={
 'all_page': 'SELECT s.*,m.username FROM studies s JOIN members m ON m.id=s.owner_id ORDER BY s.created_at DESC,s.id DESC LIMIT 20',
 'filtered_page': "SELECT s.*,m.username FROM studies s JOIN members m ON m.id=s.owner_id WHERE s.category='PROGRAMMING' AND s.level='BEGINNER' AND s.status='RECRUITING' ORDER BY s.created_at DESC,s.id DESC LIMIT 20",
 'filtered_count': "SELECT count(*) FROM studies WHERE category='PROGRAMMING' AND level='BEGINNER' AND status='RECRUITING'",
}
def plans(phase):
    result={name:[json.loads(sql('EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) '+query+';'))[0] for _ in range(5)] for name,query in queries.items()}
    (OUT/(phase+'-plans.json')).write_text(json.dumps(result,indent=2)+'\n')
def k6(phase,duration='30s'):
    args=['docker','run','--rm','--network','study-proof_default','--user','0','--env-file',str(token_file),'-e','PHASE='+phase,'-e','DURATION='+duration,
          '-v',str(ROOT/'perf')+':/scripts:ro','-v',str(OUT)+':/results','grafana/k6:1.3.0','run','/scripts/search.js']
    with (OUT/(phase+'-console.txt')).open('w') as output:
        completed=subprocess.run(args,stdout=output,stderr=subprocess.STDOUT,text=True)
    if completed.returncode: raise RuntimeError('k6 failed; inspect '+phase+'-console.txt')

metadata={'checkedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'host':platform.platform(),'environment':'local Docker Desktop, direct backend HTTP; TLS/rate-limit gateway excluded to isolate API/DB performance', 'fixtureStudies':120000,'fixtureStudyDays':240000,'arrivalRatePerRoute':20,'measurementDurationSeconds':30,'sql':queries,'baseCommit':run(['git','rev-parse','HEAD']).strip(),'sourceState':'uncommitted changes included; see final source manifest'}
metadata['backendImageId']=run(['docker','inspect','--format','{{.Image}}','study-proof-backend-1']).strip()
metadata['dockerResources']=json.loads(run(['docker','info','--format','{"cpus":{{.NCPU}},"memoryBytes":{{.MemTotal}}}']))
try:
    k6('warmup-before','10s')
    plans('before')
    k6('before')
    sql((ROOT/'perf/candidate-indexes.sql').read_text())
    sql('ANALYZE studies;')
    k6('warmup-after','10s')
    plans('after')
    k6('after')
    metadata['indexBytes']=sql("SELECT indexrelname||':'||pg_relation_size(indexrelid) FROM pg_stat_user_indexes WHERE indexrelname IN ('idx_studies_filter_newest','idx_studies_newest') ORDER BY indexrelname;")
    (OUT/'metadata.json').write_text(json.dumps(metadata,indent=2)+'\n')
    result={}
    for phase in ['before','after']:
        data=json.loads((OUT/(phase+'.json')).read_text())['metrics']
        result[phase]={name:value['values'] for name,value in data.items() if name in ['http_reqs','http_req_failed','dropped_iterations'] or name.startswith('http_req_duration{')}
    print(json.dumps(result,indent=2))
finally:
    token_file.unlink(missing_ok=True)
    # Restore the final schema even if a benchmark threshold fails.
    sql((ROOT/'perf/candidate-indexes.sql').read_text().replace('CREATE INDEX ', 'CREATE INDEX IF NOT EXISTS '))
