package com.interviewos.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.interviewos.config.AppProperties;
import com.interviewos.config.KeyValueStore;
import com.interviewos.exception.ApiError;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    // The custom filters are created here, not exposed as beans, so Boot does not register them a second time.
    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, JwtService jwt, TokenDenylist denylist, KeyValueStore store,
                                    AppProperties props, ObjectMapper mapper) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)   // stateless bearer-token API: no cookies, no CSRF surface
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    mapper.writeValue(res.getOutputStream(), ApiError.of(401, "UNAUTHENTICATED", "Authentication required"));
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType("application/json");
                    mapper.writeValue(res.getOutputStream(), ApiError.of(403, "FORBIDDEN", "Access denied"));
                }))
            .addFilterBefore(new RequestIdFilter(), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new RateLimitFilter(store, props, mapper), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new JwtAuthFilter(jwt, denylist), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration c = new CorsConfiguration();
        c.setAllowedOrigins(props.cors().allowedOrigins());   // explicit origins only, never "*"
        c.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        c.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        c.setExposedHeaders(List.of("X-Request-Id"));
        c.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", c);
        return src;
    }
}
