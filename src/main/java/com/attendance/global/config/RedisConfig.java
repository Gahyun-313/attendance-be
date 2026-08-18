package com.attendance.global.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 캐시 설정
 * StatisticsService의 통계 조회는 매번 count 쿼리 여러 개를 다시 실행하는데, 관리자 대시보드는
 * 자주 재조회되므로 결과를 잠깐 캐싱해 재사용한다.
 */
@Configuration
@EnableCaching // 이 어노테이션이 있어야 @Cacheable/@CacheEvict 같은 캐시 어노테이션이 실제로 동작한다.
public class RedisConfig {

  // 캐시 이름 상수. 문자열 리터럴을 여러 곳에 흩뿌리면 오타가 나도 컴파일 에러로 잡히지 않는다.
  public static final String CACHE_SESSION_DASHBOARD = "sessionDashboard";
  public static final String CACHE_OVERALL_STATISTICS = "overallStatistics";
  public static final String CACHE_DASHBOARD_STATISTICS = "dashboardStatistics";
  public static final String CACHE_USER_DASHBOARD = "userDashboard";
  public static final String CACHE_ATTENDACNE_RANKING = "attendanceRanking";

  /** 캐시별 TTL 등록. 데이터 성격에 따라 TTL을 다르게 둔다. */
  @Bean
  public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
    return builder ->
        builder
            // 체크인마다 값이 바뀌어야 하는 실시간 데이터라 TTL을 짧게(5초) 둔다.
            .withCacheConfiguration(
                CACHE_SESSION_DASHBOARD, defaultConfig().entryTtl(Duration.ofSeconds(5)))
            // 누적 통계는 "지금 이 순간의 스냅샷" 성격이라 오차 허용 범위가 넓어 TTL을 길게(1분) 둔다.
            .withCacheConfiguration(
                CACHE_OVERALL_STATISTICS, defaultConfig().entryTtl(Duration.ofMinutes(1)))
            .withCacheConfiguration(
                CACHE_DASHBOARD_STATISTICS, defaultConfig().entryTtl(Duration.ofMinutes(1)))
            // 사용자 대시보드도 누적 스냅샷 성격이라 동일하게 TTL을 1분으로 둔다.
            .withCacheConfiguration(
                CACHE_USER_DASHBOARD, defaultConfig().entryTtl(Duration.ofMinutes(1)))
            // 랭킹은 학생 수만큼 반복 쿼리가 나가는 무거운 집계라 캐싱 효과가 커서 TTL 1분을 둔다.
            .withCacheConfiguration(
                CACHE_ATTENDACNE_RANKING, defaultConfig().entryTtl(Duration.ofMinutes(1)));
  }

  /** 캐시 값의 직렬화 방식 공통 설정 */
  private RedisCacheConfiguration defaultConfig() {
    return RedisCacheConfiguration.defaultCacheConfig()
        // 키는 문자열로 저장해, redis-cli로 볼 때 "sessionDashboard::3"처럼 그대로 읽히게 한다.
        .serializeKeysWith(
            RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
        // 값은 JSON으로 저장해, DTO마다 Serializable을 구현하지 않아도 되고 redis-cli로 바로 읽을 수 있게 한다.
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer()))
        // null은 캐싱하지 않는다. "값이 원래 없다"와 "캐시가 아직 안 채워졌다"를 구분하기 위함이다.
        .disableCachingNullValues();
  }
}
