import http from 'k6/http';
import { check } from 'k6';
if (!__ENV.TOKEN) throw new Error('TOKEN is required');
const duration = __ENV.DURATION || '30s';
export const options = {
  scenarios: {
    all: { executor: 'constant-arrival-rate', exec: 'all', rate: 20, timeUnit: '1s', duration, preAllocatedVUs: 20, maxVUs: 20 },
    filtered: { executor: 'constant-arrival-rate', exec: 'filtered', rate: 20, timeUnit: '1s', duration, preAllocatedVUs: 20, maxVUs: 20 },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  thresholds: (__ENV.PHASE || "").startsWith("warmup") ? {} : {
    http_req_failed: ['rate<0.001'], checks: ['rate>0.999'], dropped_iterations: ['count==0'],
    'http_req_duration{route:all}': ['p(95)<500', 'p(99)<1000'],
    'http_req_duration{route:filtered}': ['p(95)<500', 'p(99)<1000'],
  },
};
function search(query, route) {
  const response = http.get(`${__ENV.BASE_URL}/api/studies?size=20&page=0${query}`, {
    headers: { Authorization: `Bearer ${__ENV.TOKEN}` }, tags: { route, name: `study-search-${route}` },
  });
  check(response, {
    'HTTP 200': r => r.status === 200,
    'nonempty page': r => r.status === 200 && r.json('content').length === 20,
  });
}
export function all() { search('', 'all'); }
export function filtered() { search('&category=PROGRAMMING&level=BEGINNER&status=RECRUITING', 'filtered'); }
export function handleSummary(data) {
  return { [`/results/${__ENV.PHASE}.json`]: JSON.stringify(data, null, 2) };
}
