package com.example.Dawson.Bungalow.config;

import com.example.Dawson.Bungalow.security.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    // Password encoder for hashing user passwords
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // AuthenticationManager bean for login/auth flows
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Main security filter chain
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // disable CSRF for APIs
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // enable global CORS
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS) // stateless sessions for JWT
                )
                .authorizeHttpRequests(auth -> auth
                        // Allow preflight requests
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                        // Public auth endpoints
                        .requestMatchers("/api/auth/**").permitAll()

                        // ── Image serving (public) ──────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/images/**").permitAll()

                        // ── Admin-only room endpoints (MUST come before the public room rule) ──
                        .requestMatchers("/api/rooms/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/rooms/admin/**").hasAuthority("ADMIN")

                        // ── Public room browsing ────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/rooms/**").permitAll()

                        // ── Reviews ────────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/reviews/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/reviews/").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/reviews/**").hasRole("ADMIN")

                        // ── Promotions ────────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET,  "/api/promotions/active").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/api/promotions/*/banner").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/promotions/validate").permitAll()
                        .requestMatchers("/api/promotions/**").hasRole("ADMIN")

                        // ── Other admin endpoints ───────────────────────────────────────
                        .requestMatchers("/api/bookings/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        .requestMatchers(HttpMethod.POST, "/chat").permitAll()
                        .requestMatchers(HttpMethod.GET,  "/health").permitAll()

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                // Add JWT filter before username/password filter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Global CORS configuration
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of(
                "http://127.0.0.1:5500",
                "http://localhost:5500",
                "https://dowson.netlify.app"
        ));

        config.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));

        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
