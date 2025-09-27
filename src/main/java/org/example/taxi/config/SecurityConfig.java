package org.example.taxi.config;

import org.example.taxi.repository.UserRepository;
import org.example.taxi.service.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    public SecurityConfig(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CustomUserDetailsService userDetailsService(UserRepository userRepository) {
        return new CustomUserDetailsService(userRepository);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider(
            CustomUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder);
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:5175"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Publicly accessible endpoints
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**",
                                "/api/public/register-session", "/api/public/get-credentials/**", "/api/public/login").permitAll()
                        // File upload endpoints (all require DRIVER role)
                        .requestMatchers("/api/uploads/driver/**").hasRole("DRIVER")
                        .requestMatchers("/api/client/uploads/profile-picture").hasRole("CLIENT")
                        // Client-specific endpoints
                        .requestMatchers("/api/client/**").hasAnyRole("ADMIN", "ETAMIN", "CLIENT")
                        // Driver-specific endpoints
                        .requestMatchers("/api/driver/**").hasAnyRole("ADMIN", "ETAMIN", "DRIVER")
                        // Operator-specific endpoints
                        .requestMatchers("/api/operator/**").hasAnyRole("ADMIN", "ETAMIN", "OPERATOR")
                        // ETAMIN-specific endpoints
                        .requestMatchers("/api/etamin/**").hasRole("ETAMIN")
                        .requestMatchers("/api/analytics/**").hasAnyRole("ADMIN", "ETAMIN")
                        // Admin-specific endpoints
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "ETAMIN")
                        // All other requests require authentication
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}