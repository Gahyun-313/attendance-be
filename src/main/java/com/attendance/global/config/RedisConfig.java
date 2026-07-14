package com.attendance.global.config;

import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 캐시 설정
 *
 * 왜 캐시가 필요한가
 * - StatisticsService의 통계 조회는 호출될 때마다 count 쿼리 여러 개(+ 그룹별 반복 집계)를 매번 다시 실행한다.
 * - 관리자 대시보드는 화면을 열어둔 채로 자주 재조회되므로, 결과를 잠깐 Redis에 담아 재사용한다.
 */
@Configuration
@EnableCaching // 이게 있어야 @Cacheable/@CacheEvict 같은 캐시 어노테이션이 실제로 동작한다
public class RedisConfig {

    // 캐시 이름을 상수로 모아둠 - 문자열 리터럴을 서비스 코드 여러 곳에 흩뿌리면 오타가 나도 컴파일 에러로 안 잡힘
    public static final String CACHE_SESSION_DASHBOARD = "sessionDashboard";
    public static final String CACHE_OVERALL_STATISTICS = "overallStatistics";
    public static final String CACHE_DASHBOARD_STATISTICS = "dashboardStatistics";

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder ->
                builder
                        // 체크인마다 값이 바뀌어야 하는 실시간 데이터라 TTL을 짧게(5초) 둔다
                        .withCacheConfiguration(
                                CACHE_SESSION_DASHBOARD, defaultConfig().entryTtl(Duration.ofSeconds(5)))
                        // 누적 통계는 "지금 이 순간의 스냅샷" 성격이라 오차 허용 범위가 넓어 TTL을 길게(1분) 둬도 된다
                        .withCacheConfiguration(
                                CACHE_OVERALL_STATISTICS, defaultConfig().entryTtl(Duration.ofMinutes(1)))
                        .withCacheConfiguration(
                                CACHE_DASHBOARD_STATISTICS, defaultConfig().entryTtl(Duration.ofMinutes(1)));
    }

    /** 캐시 값 직렬화 방식 공통 설정 */
    private RedisCacheConfiguration defaultConfig() {
        return RedisCacheConfiguration.defaultCacheConfig()
                // 키는 문자열로 저장 - redis-cli로 볼 때 "sessionDashboard::3"처럼 그대로 읽힌다
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new StringRedisSerializer()))
                // 값은 JSON으로 저장 - JDK 기본 직렬화(Serializable)를 DTO마다 구현 안 해도 되고,
                // redis-cli로 캐시에 실제로 뭐가 들어갔는지 사람이 눈으로 바로 읽을 수 있어 디버깅이 쉽다
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                                new GenericJackson2JsonRedisSerializer()))
                // null은 캐싱하지 않음 - 캐싱하면 "값이 원래 없다"와 "캐시가 아직 안 채워졌다"를 구분 못 하게 됨
                .disableCachingNullValues();
    }
}