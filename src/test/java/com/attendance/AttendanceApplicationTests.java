package com.attendance;

import com.attendance.global.config.RedissonTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(RedissonTestConfig.class)
@SpringBootTest
class AttendanceApplicationTests {

  @Test
  void contextLoads() {}
}
