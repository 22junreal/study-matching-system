package com.studymatching.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id @Column(length = 64) private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private RefreshSession session;
    @Column(nullable = false) private Instant createdAt;
    private Instant consumedAt;

    protected RefreshToken() {}

    public RefreshToken(String tokenHash, RefreshSession session, Instant now) {
        this.tokenHash = tokenHash;
        this.session = session;
        this.createdAt = now;
    }

    public boolean isConsumed() { return consumedAt != null; }
    public void consume(Instant now) { consumedAt = now; }
}
