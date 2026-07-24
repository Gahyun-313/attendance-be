package com.attendance.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * 외부 API(구글/카카오 소셜 로그인 사용자 정보 조회) 호출용 RestTemplate 빈 등록
 * - GoogleOAuthClient, KakaoOAuthClient에서 사용
 */
@Configuration
public class RestTemplateConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
