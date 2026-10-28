import http from 'k6/http';
import { check, sleep } from 'k6';

if (!__ENV.TOKEN || !__ENV.RUN_ID) throw new Error('TOKEN and RUN_ID are required');
const stages = [
  ['baseline', 1, 45, 0],
  ['medium', 10, 45, 45],
  ['high', 30, 60, 90],
  ['peak', 60, 60, 150],
  ['recovery', 1, 90, 210],
];
const scenarios = {};
const thresholds = { http_req_failed: ['rate<0.01'], checks: ['rate>0.99'] };
for (const [name, vus, duration, start] of stages) {
  scenarios[name] = { executor: 'constant-vus', vus, duration: `${duration}s`,
    startTime: `${start}s`, gracefulStop: '10s', tags: { phase: name } };
  // Materialize per-phase summaries; this is a timeout guard, not an SLA.
  thresholds[`http_req_duration{phase:${name}}`] = ['p(95)<15000'];
}
export const options = {
  scenarios, thresholds, tags: { run_id: __ENV.RUN_ID, experiment: 'natural-load' },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  discardResponseBodies: true,
};
export function setup() { return { startedAt: new Date().toISOString() }; }
export default function () {
  const response = http.get(`${__ENV.BASE_URL}/api/studies?page=0&size=100`, {
    headers: { Authorization: `Bearer ${__ENV.TOKEN}` },
    tags: { name: 'GET /api/studies (100 rows)' }, timeout: '15s',
  });
  check(response, { 'HTTP 200': r => r.status === 200 });
  sleep(0.05);
}
export function handleSummary(data) {
  return { '/results/summary.json': JSON.stringify(data, null, 2) };
}
