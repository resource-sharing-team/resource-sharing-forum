package com.resourcesharing.forum;

import com.resourcesharing.forum.security.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class ForumBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForumBackendApplication.class, args);
    }
}

