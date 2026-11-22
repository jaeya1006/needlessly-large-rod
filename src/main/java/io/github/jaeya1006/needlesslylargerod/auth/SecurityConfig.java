package io.github.jaeya1006.needlesslylargerod.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;

@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    /** 로그인 없이 부를 수 있는 API. 나머지 /api/** 는 로그인 필요, 화면 파일은 누구나. */
    static final String ISSUER = "needlessly-large-rod";

    static final String[] PUBLIC_API = {"/api/auth/signup", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout"};

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenResolver resolver) throws Exception {
        http
                // CSRF 토큰 대신: 쿠키는 SameSite, 그리고 SameOriginFilter가 다른 출처의 POST/PUT/DELETE를 막는다
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(f -> f.disable())
                .httpBasic(h -> h.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers(PUBLIC_API).permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new SameOriginFilter(), CsrfFilter.class)
                .oauth2ResourceServer(o -> o.bearerTokenResolver(resolver).jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    BearerTokenResolver bearerTokenResolver() {
        return new CookieBearerTokenResolver();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecretKey jwtKey(@Value("${JWT_SECRET:}") String secret) {
        if (secret.isBlank()) {
            // 키가 없으면 실행할 때마다 새로 만든다 → 재시작하면 모두 로그아웃. .env의 JWT_SECRET을 채우면 유지된다.
            log.warn("JWT_SECRET이 비어 있어 임시 키를 만듭니다. 서버를 다시 켜면 모두 로그아웃됩니다.");
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            secret = HexFormat.of().formatHex(random);
        }
        return new SecretKeySpec(sha256(secret), "HmacSHA256"); // 길이에 상관없이 256비트 키
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        // 기본 검사(만료·nbf)에 더해 우리가 발급한 토큰인지(iss), 만료 시각이 꼭 있는지(exp) 본다
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(ISSUER),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull)));
        return decoder;
    }

    static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
