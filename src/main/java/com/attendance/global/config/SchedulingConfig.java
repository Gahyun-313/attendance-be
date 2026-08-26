package com.attendance.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 배치성 스케줄러(@Scheduled) 활성화 설정 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
