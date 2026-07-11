/**
 * 출석 체크인 API 응답 시간 측정 (Before)
 *
 * 설계 의도:
 * - 로그인은 setup()에서 미리 끝내둔다. VU 실행 구간(default 함수)에서 로그인까지 같이 재면
 *   "체크인 API 자체의 응답 시간"이 아니라 "로그인 + 체크인" 시간이 섞여버리기 때문.
 * - executor를 'per-vu-iterations'로 써서 VU 1개 = 학생 계정 1개가 정확히 1:1로 매칭되게 한다.
 *   (shared-iterations를 쓰면 여러 VU가 같은 인덱스를 동시에 가리킬 수 있어 토큰이 꼬일 수 있음)
 * - config.json에 학생 계정이 1개뿐이면 최초 1건만 "신규 체크인"이고 그 뒤로는 이 스크립트를
 *   재실행할 때마다 409(중복)만 뜬다. 그것도 유효한 측정치이니(중복 체크 로직의 응답 시간) 문제 없음.
 */
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const config = JSON.parse(open('./config.json'));

const checkinDuration = new Trend('checkin_duration_ms');
const successCount = new Counter('checkin_success_count'); // 201 (신규 체크인)
const duplicateCount = new Counter('checkin_duplicate_count'); // 409 (이미 체크인됨)
const failCount = new Counter('checkin_fail_count'); // 그 외 예상 못한 응답

export const options = {
    scenarios: {
        checkin_response_time: {
            executor: 'per-vu-iterations',
            vus: config.students.length,
            iterations: 1,
            maxDuration: '1m',
        },
    },
};

export function setup() {
    const tokens = config.students.map((student) => {
        const res = http.post(
            `${config.baseUrl}/api/auth/login`,
            JSON.stringify({ username: student.username, password: student.password }),
            { headers: { 'Content-Type': 'application/json' } }
        );
        const body = JSON.parse(res.body);
        if (!body.data || !body.data.accessToken) {
            throw new Error(`로그인 실패 (${student.username}): ${res.body}`);
        }
        return body.data.accessToken;
    });
    return { tokens };
}

export default function (data) {
    // VU는 1부터 시작하므로 -1 해서 0-based 인덱스로 맞춤
    const token = data.tokens[(__VU - 1) % data.tokens.length];

    const res = http.post(
        `${config.baseUrl}/api/attendances/check-in`,
        JSON.stringify({ nfcTagUid: config.nfcTagUid }),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${token}`,
            },
        }
    );

    // 커스텀 Trend에 응답시간을 기록 - k6 기본 http_req_duration과 별개로,
    // 이 요청만 따로 뽑아서 avg/p95를 보고자 별도 metric으로 관리
    checkinDuration.add(res.timings.duration);

    if (res.status === 201) {
        successCount.add(1);
    } else if (res.status === 409) {
        duplicateCount.add(1);
    } else {
        failCount.add(1);
        console.warn(`예상 밖 응답 status=${res.status} body=${res.body}`);
    }

    check(res, {
        '체크인 응답이 201(신규) 또는 409(중복) 중 하나': (r) => r.status === 201 || r.status === 409,
    });
}