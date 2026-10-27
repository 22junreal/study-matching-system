package com.studymatching.common.config;

import com.studymatching.common.exception.ErrorCode;
import com.studymatching.common.exception.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@Profile("monitoring")
public class MonitoringSecurityConfig {
    @Bean
    @Order(1)
    SecurityFilterChain monitoringSecurityFilterChain(HttpSecurity http, PasswordEncoder encoder,
            ObjectMapper json, @Value("${monitoring.password}") String password) throws Exception {
        if (password.length() < 32 || password.isBlank()) {
            throw new IllegalArgumentException("Monitoring password must have at least 32 characters");
        }
        var users = new InMemoryUserDetailsManager(User.withUsername("prometheus")
                .password(encoder.encode(password)).roles("MONITORING").build());
        var provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(encoder);
        AuthenticationEntryPoint unauthorized = (request, response, exception) -> {
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "Basic realm=\"metrics\"");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getOutputStream(), ErrorResponse.of(ErrorCode.UNAUTHORIZED));
        };
        return http.securityMatcher("/actuator/prometheus", "/actuator/prometheus/**")
                // No parent manager: application users cannot authenticate here.
                .authenticationManager(new ProviderManager(provider))
                .csrf(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.GET, "/actuator/prometheus")
                        .hasRole("MONITORING").anyRequest().denyAll())
                .httpBasic(b -> b.authenticationEntryPoint(unauthorized))
                .exceptionHandling(e -> e.authenticationEntryPoint(unauthorized))
                .build();
    }
}
