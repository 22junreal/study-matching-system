#!/usr/bin/env python3
"""Start an isolated local monitoring demo; never modifies the development stack."""
import base64
import os
from pathlib import Path
import secrets
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
STATE = Path('/private/tmp' if Path('/private/tmp').exists() else tempfile.gettempdir()) / 'study-monitoring'
STATE.mkdir(mode=0o700, exist_ok=True)
os.chmod(STATE, 0o700)
secret_dir = STATE / 'secrets'
secret_dir.mkdir(mode=0o700, exist_ok=True)
for name in ['monitoring.password', 'grafana.password']:
    path = secret_dir / name
    if not path.exists():
        fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        with os.fdopen(fd, 'w') as out:
            out.write(secrets.token_urlsafe(36))
    # Parent is host-private; non-root container users must read the mounted file.
    os.chmod(path, 0o444)
env = STATE / 'environment'
if not env.exists():
    content = ('POSTGRES_DB=study_monitoring\nPOSTGRES_USER=monitor_user\nPOSTGRES_PASSWORD=' + secrets.token_urlsafe(36)
        + '\nJWT_SECRET=' + base64.b64encode(secrets.token_bytes(48)).decode()
        + '\nCORS_ALLOWED_ORIGINS=http://localhost:13000\nPORT=18082\nBACKEND_BIND_ADDRESS=127.0.0.1'
        + '\nPROMETHEUS_PORT=19090\nGRAFANA_PORT=13000\nMONITORING_SECRETS_DIR=' + str(secret_dir)
        + '\nSITE_ADDRESS=localhost\nTLS_MODE=tls internal\nEDGE_BIND_ADDRESS=127.0.0.1\nHTTP_PORT=18083\nHTTPS_PORT=18444\n')
    fd = os.open(env, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as out:
        out.write(content)
compose = ['docker', 'compose', '--env-file', str(env), '-p', 'study-monitoring',
           '-f', str(ROOT/'infra/compose.prod.yml'), '-f', str(ROOT/'infra/compose.monitoring.yml'),
           '-f', str(ROOT/'infra/compose.edge.yml')]
subprocess.run(compose + ['config', '-q'], check=True)
subprocess.run(compose + ['up', '-d', '--build', '--wait', '--wait-timeout', '240'], check=True)
subprocess.run(compose + ['exec', '-T', 'prometheus', 'promtool', 'check', 'config', '/tmp/prometheus.yml'], check=True)
subprocess.run(compose + ['kill', '--signal', 'SIGHUP', 'prometheus'], check=True)
subprocess.run(compose + ['cp', 'caddy:/data/caddy/pki/authorities/local/root.crt', str(STATE/'ca.crt')], check=True)
print('Grafana: http://localhost:13000/d/study-backend (user: admin)')
print('Grafana password file:', secret_dir/'grafana.password')
print('Prometheus: http://localhost:19090; environment file:', env)
