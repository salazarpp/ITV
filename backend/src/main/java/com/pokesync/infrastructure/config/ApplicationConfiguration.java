package com.pokesync.infrastructure.config;

import com.pokesync.application.port.out.PasswordHasher;
import com.pokesync.application.port.out.TokenIssuer;
import com.pokesync.application.port.out.UserRepository;
import com.pokesync.application.service.AuthService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationConfiguration {
    @Bean
    AuthService authService(UserRepository users, PasswordHasher passwords, TokenIssuer tokens) {
        return new AuthService(users, passwords, tokens);
    }
}
