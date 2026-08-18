package com.attendance.global.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/** Firebase Admin SDK 초기화 테스트 프로파일(firebase.enabled=false)에서는 이 빈을 생성하지 않는다. */
@Slf4j
@Configuration
@ConditionalOnProperty(
    prefix = "firebase",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class FirebaseConfig {

  @Value("${firebase.credentials-base64}")
  private String credentialsBase64;

  @PostConstruct
  public void init() {
    try {
      // 서비스 계정 키(JSON) base64 디코드
      byte[] decoded = Base64.getDecoder().decode(credentialsBase64);
      GoogleCredentials credentials =
          GoogleCredentials.fromStream(new ByteArrayInputStream(decoded));
      FirebaseOptions options = FirebaseOptions.builder().setCredentials(credentials).build();

      if (FirebaseApp.getApps().isEmpty()) {
        FirebaseApp.initializeApp(options);
      }
    } catch (Exception e) {
      log.error("Firebase 초기화 실패 - firebase.credentials-base64 값을 확인하세요", e);
      throw new IllegalStateException("Firebase Admin SDK 초기화 실패", e);
    }
  }
}
