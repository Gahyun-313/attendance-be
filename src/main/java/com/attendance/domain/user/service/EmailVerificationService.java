package com.attendance.domain.user.service;

import com.attendance.domain.organization.entity.Organization;
import com.attendance.domain.organization.repository.OrganizationRepository;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.DuplicateException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import java.security.SecureRandom;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * 이메일 인증 코드 발급/검증 - 소셜 로그인이 막힌 환경(회사 네트워크 등)에서 단체 조인을 위한
 * 대체 경로. Organization.code + 이메일 인증만으로 신규 어드민 계정을 만들 수 있게 한다.
 *
 * <p>인증 코드는 DB가 아닌 Redis에 TTL(5분)로 저장한다 - 짧게 살고 자동으로 사라져야 하는
 * 값이라 만료 처리를 위한 별도 배치/스케줄러가 필요 없는 Redis가 더 적합하다고 판단했다.
 * 값에는 "코드가 어느 단체 조인 요청이었는지"도 함께 담아서, 검증 시 이메일+코드만으로
 * organizationId를 바로 얻을 수 있게 한다 (클라이언트가 organizationId를 다시 보낼 필요 없음).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final String KEY_PREFIX = "email-verify:";
    private static final String RESET_KEY_PREFIX = "password-reset:"; // 조인용 코드와 완전히 분리된 키 공간
    private static final Duration CODE_TTL = Duration.ofMinutes(5);

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    /** 인증 코드 발급 + 이메일 발송 */
    public void sendVerificationCode(String organizationCode, String email) {
        Organization organization =
                organizationRepository
                        .findByCode(organizationCode)
                        .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ORGANIZATION_CODE));

        // 이미 가입된 이메일이면 애초에 코드를 보낼 필요가 없음 - 조인 시점이 아니라 발송 시점에 미리 걸러줌
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateException(ErrorCode.DUPLICATE_EMAIL);
        }

        String code = generateCode();
        // "단체ID:코드" 형태로 저장 - 검증 시 이 값 하나로 단체와 코드를 함께 확인
        String value = organization.getId() + ":" + code;
        redisTemplate.opsForValue().set(KEY_PREFIX + email, value, CODE_TTL);

        sendEmail(email, code);
        log.info("이메일 인증 코드 발송 완료 - email: {}, organizationId: {}", email, organization.getId());
    }

    /**
     * 인증 코드 검증 - 성공 시 1회용으로 즉시 삭제하고 organizationId 반환
     *
     * @return 검증된 코드에 연결된 organizationId
     */
    public Long verifyAndConsume(String email, String code) {
        String key = KEY_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }

        String[] parts = stored.split(":", 2);
        Long organizationId = Long.valueOf(parts[0]);
        String storedCode = parts[1];

        if (!storedCode.equals(code)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }

        redisTemplate.delete(key); // 재사용 방지(1회용)
        return organizationId;
    }

    /**
     * 비밀번호 재설정 인증 코드 발급 + 이메일 발송 (로그아웃 상태 - 비밀번호를 잊은 사용자 대상)
     * 조인 코드와 달리 organizationId를 값에 담을 필요가 없다 - 검증 후 이메일로 기존 User를 다시 조회하면 되기 때문
     */
    public void sendPasswordResetCode(String email) {
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
        if (user.getPassword() == null) {
            throw new BusinessException(ErrorCode.SOCIAL_ACCOUNT_NO_PASSWORD);
        }

        String code = generateCode();
        redisTemplate.opsForValue().set(RESET_KEY_PREFIX + email, code, CODE_TTL);
        sendResetEmail(email, code);
        log.info("비밀번호 재설정 인증 코드 발송 완료 - email: {}", email);
    }

    /**
     * 비밀번호 재설정 코드 검증 - 성공 시 1회용으로 즉시 삭제 (조인 코드와 별개 키 공간이라 서로 간섭 없음)
     */
    public void verifyPasswordResetCodes(String email, String code) {
        String key = RESET_KEY_PREFIX + email;
        String stored = redisTemplate.opsForValue().get(key);
        if (stored == null) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_EXPIRED);
        }
        if (!stored.equals(code)) {
            throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_INVALID);
        }
        redisTemplate.delete(key);
    }

    private void sendResetEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[출석하자] 비밀번호 재설정 인증 코드");
        message.setText("인증 코드: " + code + "\n5분 이내에 입력해주세요. 본인이 요청하지 않았다면 이 메일을 무시하세요.");
        mailSender.send(message);
    }

    /** 6자리 숫자 인증 코드 생성 - 000000~999999, 앞자리 0도 유지되도록 %06d로 포맷 */
    private String generateCode() {
        int number = new SecureRandom().nextInt(1_000_000);
        return String.format("%06d", number);
    }

    private void sendEmail(String to, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject("[출석하자] 단체 가입 이메일 인증 코드");
        message.setText("인증 코드: " + code + "\n5분 이내에 입력해주세요. 본인이 요청하지 않았다면 이 메일을 무시하세요.");
        mailSender.send(message);
    }
}