package com.chaptime.backend.config;

import com.chaptime.backend.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    /**
     * Constructs a new SecurityConfig instance with the specified JwtAuthFilter.
     *
     * @param jwtAuthFilter The JwtAuthFilter to be used for processing JWT authentication
     *                       within the security configuration.
     */
    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    /**
     * Configures a SecurityFilterChain bean to establish the security configuration for HTTP requests.
     * This method sets up the following:
     * - Disables CSRF protection.
     * - Configures stateless session management.
     * - Adds a JWT authentication filter before the default UsernamePasswordAuthenticationFilter.
     * - Specifies URL-based authorization rules, allowing all users to access the signup endpoint
     *   and requiring authentication for other endpoints under "/api/v1/**".
     *
     * @param http The {@link HttpSecurity} instance used to configure the security features for the application.
     * @return The configured {@link SecurityFilterChain} instance.
     * @throws Exception If an error occurs during the configuration process.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/signup").permitAll()
                        .requestMatchers("/api/v1/**").hasRole("USER")
                )
                .build();
    }
}