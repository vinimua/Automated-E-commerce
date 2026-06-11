package com.tk.ai.video.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.tk.ai.video.module.**.mapper")
public class MyBatisPlusConfig {
}
