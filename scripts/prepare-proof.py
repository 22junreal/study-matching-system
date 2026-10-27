#!/usr/bin/env python3
"""Create/reuse only the isolated local study-proof stack. No cloud resources."""
import base64, os, pathlib, secrets, subprocess
ROOT = pathlib.Path(__file__).resolve().parents[1]
env = pathlib.Path('/private/tmp/study-proof.env')
if not env.exists():
    env.write_text('POSTGRES_DB=study_proof\nPOSTGRES_USER=proof_user\nPOSTGRES_PASSWORD=' + secrets.token_urlsafe(32)
        + '\nJWT_SECRET=' + base64.b64encode(secrets.token_bytes(48)).decode()
        + '\nCORS_ALLOWED_ORIGINS=https://localhost:18443\nPORT=18081\nBACKEND_BIND_ADDRESS=127.0.0.1'
        + '\nSITE_ADDRESS=localhost\nTLS_MODE=tls internal\nEDGE_BIND_ADDRESS=127.0.0.1\nHTTP_PORT=18080\nHTTPS_PORT=18443\n')
os.chmod(env, 0o600)
compose = ['docker','compose','--env-file',str(env),'-p','study-proof','-f',str(ROOT/'infra/compose.prod.yml'),'-f',str(ROOT/'infra/compose.edge.yml')]
subprocess.run(compose + ['config','-q'], check=True)
subprocess.run(compose + ['up','-d','--build','--wait','--wait-timeout','180'], check=True)
subprocess.run(compose + ['cp','caddy:/data/caddy/pki/authorities/local/root.crt','/private/tmp/study-proof-ca.crt'], check=True)
print('Local proof stack ready: https://localhost:18443 (trust /private/tmp/study-proof-ca.crt explicitly)')
