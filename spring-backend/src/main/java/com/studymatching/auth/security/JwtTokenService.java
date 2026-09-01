package com.studymatching.auth.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final long expirationSeconds;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            @Value("${jwt.access-token-expiration-seconds}")
            long expirationSeconds
    ) {
        this.jwtEncoder = jwtEncoder;
        this.expirationSeconds = expirationSeconds;
    }

    public String createAccessToken(String username) {

        Instant now = Instant.now();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("study-matching-api")
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(expirationSeconds))
                .build();

        JwsHeader header = JwsHeader
                .with(MacAlgorithm.HS256)
                .build();

        return jwtEncoder
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }
}