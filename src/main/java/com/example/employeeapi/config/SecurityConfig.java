package com.example.employeeapi.config;

import com.example.employeeapi.error.ApiErrorWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

@Configuration
public class SecurityConfig {

    public static final String SCOPE_WRITE = "SCOPE_transactions:write";
    public static final String SCOPE_READ = "SCOPE_transactions:read";

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiErrorWriter errorWriter) throws Exception {
        AuthenticationEntryPoint entryPoint = (request, response, ex) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            errorWriter.write(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                    "Missing, invalid or expired access token");
        };
        AccessDeniedHandler accessDeniedHandler = (request, response, ex) ->
                errorWriter.write(response, HttpStatus.FORBIDDEN, "INSUFFICIENT_SCOPE",
                        "Access token lacks the required scope");

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/token").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/transactions/**").hasAuthority(SCOPE_WRITE)
                        .requestMatchers(HttpMethod.GET, "/api/v1/transactions/**").hasAuthority(SCOPE_READ)
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> { })
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
