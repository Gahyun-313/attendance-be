/**
 * 동시 요청 중복 출석 테스트 (Before)
 *
 * 목적: 같은 학생이 같은 세션에 "동시에" 여러 번 체크인을 시도하면 지금 코드가 어떻게 반응하는지 관찰한다.
 * 지금은 Redis 분산 락이 없고(Day 6 예정), AttendanceRecord 엔티티에도 @Version(낙관적 락)이 없다.
 * DB의 UNIQUE KEY(user_id, session_id) 제약만 최후 방어선인 상태.
 *
 * 예상 가능한 시나리오 두 가지 (실행해보기 전까진 어느 쪽인지 알 수 없음, 그래서 측정하는 것):
 *
 * [시나리오 A] 세션 시작 시 이 학생의 WAITING 레코드가 미리 생성돼 있지 않은 경우
 *   -> 여러 요청이 동시에 "기존 레코드 없음"을 보고 각자 INSERT를 시도
 *   -> DB UNIQUE 제약 덕분에 1건만 성공(INSERT), 나머지는 제약 위반 예외
 *   -> 그런데 이 예외는 DuplicateException(커스텀)이 아니라 Hibernate/DB 레벨 예외라
 *      GlobalExceptionHandler의 catch-all(Exception.class)로 떨어져 500으로 응답될 가능성이 높음
 *      (커스텀 409 응답이 아니라 못생긴 500이 뜬다는 것 자체가 "고쳐야 할 지점"을 알려주는 신호)
 *
 * [시나리오 B] WAITING 레코드가 이미 있는 경우 (세션에 groupName이 있고 이 학생이 그 그룹 소속)
 *   -> 여러 요청이 동시에 "같은 기존 레코드"를 찾아서 각자 UPDATE(dirty checking)를 시도
 *   -> 낙관적 락(@Version)이 없으므로 DB가 충돌을 감지하지 못하고 마지막에 커밋된 값이 조용히 이김
 *   -> 즉 에러가 하나도 안 뜨고 여러 건이 다 200/201 "성공"으로 보일 수 있음 (Lost Update)
 *   -> 겉으로는 아무 문제 없어 보이지만 사실은 더 위험한 상태 (오류 로그조차 안 남음)
 *
 * config.json의 concurrencyStudent를 세션의 groupName에 속하지 않는 학생으로 설정하면 시나리오 A를,
 * 속하는 학생으로 설정하면 시나리오 B를 관찰할 수 있다. 두 경우 다 재현해보는 것을 권장.
 */
import http from 'k6/http';
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const config = JSON.parse(open('./config.json'));

const duration = new Trend('checkin_concurrent_duration_ms');
const successCount = new Counter('checkin_success'); // 201
const duplicateCount = new Counter('checkin_duplicate_409'); // 409 (의도한 정상 방어)
const serverErrorCount = new Counter('checkin_server_error_500'); // 500 (미처리 예외 - 문제 신호)
const otherCount = new Counter('checkin_other_status');

export const options = {
    scenarios: {
        concurrent_checkin: {
            executor: 'per-vu-iterations',
            vus: config.concurrencyVUs || 20,
            iterations: 1, // VU당 딱 1번 - "동시에 한 번씩 몰려서 요청"하는 상황을 흉내
            maxDuration: '30s',
        },
    },
};

export function setup() {
    const res = http.post(
        `${config.baseUrl}/api/auth/login`,
        JSON.stringify({
            username: config.concurrencyStudent.username,
            password: config.concurrencyStudent.password,
        }),
        { headers: { 'Content-Type': 'application/json' } }
    );
    const body = JSON.parse(res.body);
    if (!body.data || !body.data.accessToken) {
        throw new Error(`학생 로그인 실패: ${res.body}`);
    }
    return { token: body.data.accessToken };
}

export default function (data) {
    // 모든 VU가 "같은 학생 토큰"으로 "같은 태그"에 동시에 체크인을 시도한다 - 이게 이 테스트의 핵심
    const res = http.post(
        `${config.baseUrl}/api/attendances/check-in`,
        JSON.stringify({ nfcTagUid: config.nfcTagUid }),
        {
            headers: {
                'Content-Type': 'application/json',
                Authorization: `Bearer ${data.token}`,
            },
        }
    );

    duration.add(res.timings.duration);

    if (res.status === 201) {
        successCount.add(1);
    } else if (res.status === 409) {
        duplicateCount.add(1);
    } else if (res.status === 500) {
        serverErrorCount.add(1);
        console.error(`500 발생 - body: ${res.body}`);
    } else {
        otherCount.add(1);
        console.warn(`예상 밖 status=${res.status} body=${res.body}`);
    }

    check(res, {
        '최소한 크래시는 아님(500이 아님)': (r) => r.status !== 500,
    });
}