/**
 * 계 조회 API 응답 시간 측정 (Before)
 *
 * 설계 의도:
 * - 체크인과 달리 이 API들은 순수 조회(GET)라 몇 번을 반복 호출해도 부작용이 없다.
 *   그래서 ramping-vus로 점점 부하를 늘려가며 실제 "여러 관리자가 동시에 대시보드를 볼 때"
 *   와 비슷한 상황을 흉내낸다.
 * - group()으로 두 엔드포인트를 감싸면 k6 콘솔 요약에 그룹별로 나뉘어 출력되어,
 *   "대시보드 조회"와 "전체 통계 조회" 중 어느 쪽이 더 느린지 바로 비교할 수 있다.
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';

const config = JSON.parse(open('./config.json'));

export const options = {
    scenarios: {
        stats_response_time: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: 10 }, // 0 -> 10명까지 10초에 걸쳐 점증
                { duration: '20s', target: 10 }, // 10명 유지하며 20초간 반복 호출
                { duration: '5s', target: 0 }, // 마무리
            ],
        },
    },
};

export function setup() {
    const res = http.post(
        `${config.baseUrl}/api/auth/login`,
        JSON.stringify({ username: config.admin.username, password: config.admin.password }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    const body = JSON.parse(res.body);
    if (!body.data || !body.data.accessToken) {
        throw new Error(`ADMIN 로그인 실패: ${res.body}`);
    }
    return { token: body.data.accessToken };
}

export default function (data) {
    const headers = { Authorization: `Bearer ${data.token}` };

    group('dashboard 통계 조회', function () {
        const res = http.get(`${config.baseUrl}/api/statistics/dashboard`, { headers });
        check(res, { '200 OK': (r) => r.status === 200 });
    });

    group('전체 통계 조회', function () {
        const res = http.get(`${config.baseUrl}/api/statistics/overall`, { headers });
        check(res, { '200 OK': (r) => r.status === 200 });
    });

    sleep(1); // 실제 사용자가 화면을 보고 다음 액션까지 쉬는 시간을 흉내
}