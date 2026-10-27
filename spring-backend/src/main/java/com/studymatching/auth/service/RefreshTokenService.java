package com.studymatching.auth.service;

import com.studymatching.auth.dto.LoginResponse;
import com.studymatching.auth.entity.RefreshSession;
import com.studymatching.auth.entity.RefreshToken;
import com.studymatching.auth.exception.InvalidRefreshTokenException;
import com.studymatching.auth.repository.RefreshSessionRepository;
import com.studymatching.auth.repository.RefreshTokenRepository;
import com.studymatching.auth.security.JwtTokenService;
import com.studymatching.member.entity.Member;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {
    private final RefreshSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final JwtTokenService jwt;
    private final long lifetimeSeconds;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(RefreshSessionRepository sessions, RefreshTokenRepository tokens,
                               JwtTokenService jwt,
                               @Value("${jwt.refresh-token-expiration-seconds}") long lifetimeSeconds) {
        if (lifetimeSeconds <= 0) throw new IllegalArgumentException("Refresh lifetime must be positive");
        this.sessions = sessions;
        this.tokens = tokens;
        this.jwt = jwt;
        this.lifetimeSeconds = lifetimeSeconds;
    }

    @Transactional
    public LoginResponse issue(Member member) {
        Instant now = Instant.now();
        RefreshSession session = sessions.save(new RefreshSession(member, now, now.plusSeconds(lifetimeSeconds)));
        return issuePair(session, now);
    }

    // Replay revocation must COMMIT even though the endpoint responds with 401.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public LoginResponse rotate(String rawToken) {
        String hash = hash(rawToken);
        RefreshSession session = sessions.findLockedById(tokens.findSessionId(hash)
                .orElseThrow(InvalidRefreshTokenException::new))
                .orElseThrow(InvalidRefreshTokenException::new);
        Instant now = Instant.now();
        if (!session.isActive(now)) throw new InvalidRefreshTokenException();
        RefreshToken token = tokens.findById(hash).orElseThrow(InvalidRefreshTokenException::new);
        if (token.isConsumed()) {
            session.revoke(now);
            throw new InvalidRefreshTokenException();
        }
        token.consume(now);
        return issuePair(session, now);
    }

    @Transactional
    public void logout(String rawToken) {
        // Any token from this family (including a consumed token) may revoke it.
        tokens.findSessionId(hash(rawToken)).flatMap(sessions::findLockedById)
                .ifPresent(session -> session.revoke(Instant.now()));
    }

    @Scheduled(fixedDelayString = "${jwt.refresh-cleanup-interval-ms:3600000}",
               initialDelayString = "${jwt.refresh-cleanup-interval-ms:3600000}")
    @Transactional
    public int deleteExpiredSessions() {
        // DB cascade deletes token history only after the whole family has expired.
        return sessions.deleteExpired(Instant.now());
    }

    private LoginResponse issuePair(RefreshSession session, Instant now) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(hash(rawToken), session, now));
        return new LoginResponse(jwt.createAccessToken(session.getMember().getUsername()), "Bearer",
                rawToken, jwt.getExpirationSeconds(), Duration.between(now, session.getExpiresAt()).toSeconds());
    }

    private static String hash(String rawToken) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new InvalidRefreshTokenException();
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
