package com.attendance.global.config;

import org.mockito.Mockito;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * 통합 테스트(@SpringBootTest) 전용 - 실제 Redis 서버 없이도 전체 스프링 컨텍스트가 뜨게 하기 위한 설정
 *
 * <p>[왜 필요한가 - 지연 연결 vs 즉시 연결] 캐싱에 쓰는 Lettuce(spring-boot-starter-data-redis)는 연결 객체를 만드는 시점엔 아무 일도
 * 안 하다가, 실제로 명령을 보낼 때가 돼서야 연결을 시도하는 "지연 연결" 방식이라 Redis가 없어도 빈 생성 자체는 문제없었다. 반면
 * redisson-spring-boot-starter는 클래스패스에 있기만 하면 spring.data.redis.host/port를 그대로 읽어서 RedissonClient
 * 빈을 자동 생성하는데, 이건 빈을 "만드는" 바로 그 순간에 실제로 Redis 서버 연결을 시도하는 "즉시 연결" 방식이다 - 로컬에 Redis가 안 떠 있으면 이 빈을
 * 만드는 단계에서부터 실패해버린다.
 *
 * <p>[왜 상관없는 테스트까지 영향을 받는가] @SpringBootTest는 애플리케이션의 거의 모든 빈을 한꺼번에 만드는(컨텍스트를 띄우는) 방식으로 동작한다.
 * AttendanceService가 생성자에서 RedissonClient를 필요로 하기 때문에, 컨텍스트를 띄우는 과정에서 AttendanceService를 만들려면 그 전에
 * RedissonClient부터 만들어야 한다. 그 결과 로그인만 테스트하는 AuthIntegrationTest처럼 체크인과 전혀 상관없는 테스트조차, 컨텍스트 로딩 과정에서
 * AttendanceService가 같이 만들어지기 때문에 로컬 Redis가 없으면 전부 같이 실패해버린다.
 *
 * <p>[해결 방법] 이 설정을 @Import한 테스트는 진짜 RedissonClient 대신, 실제로 아무 서버에도 연결을 시도하지 않는 Mockito
 * mock을 @Primary로 대신 주입받는다. 그래서 락 관련 코드를 실제로 호출하지 않는 테스트는 이 mock을 아무 스텁 없이 그냥 받기만 해도 컨텍스트가 정상적으로
 * 뜬다. 체크인처럼 분산 락을 실제로 타는 흐름을 검증해야 하는 AttendanceFlowIntegrationTest는 같은 mock을 @Autowired로 받은 뒤
 * 자체 @BeforeEach에서 tryLock() 등을 추가로 스텁해서 사용한다.
 *
 * <p>[StringRedisTemplate까지 같이 깨지는 문제] redisson-spring-boot-starter가 클래스패스에 있으면 Spring Boot가
 * StringRedisTemplate(EmailVerificationService가 인증 코드 저장에 사용)에도 RedissonClient 기반
 * RedissonConnectionFactory를 자동으로 물린다. 그래서 RedissonClient를 스텁 없는 mock으로 바꿔버리면 분산 락뿐 아니라
 * "진짜 Redis 값 저장/조회"까지 mock 위에서 동작하게 되어 getConfig() 등에서 NullPointerException이 난다. 아래
 * redisConnectionFactory() 빈으로 StringRedisTemplate이 쓸 커넥션 팩토리를 로컬 Redis(application-local.yml과 동일한
 * localhost:6379)로 직접 분리해줘서, 분산 락 관련 코드만 mock을 타고 실제 값 저장/조회는 로컬 Redis를 그대로 쓰게 한다.
 */
@TestConfiguration
public class RedissonTestConfig {

  @Bean
  @Primary
  public RedissonClient redissonClient() {
    return Mockito.mock(RedissonClient.class);
  }

  @Bean
  @Primary
  public RedisConnectionFactory redisConnectionFactory() {
    return new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6379));
  }
}
