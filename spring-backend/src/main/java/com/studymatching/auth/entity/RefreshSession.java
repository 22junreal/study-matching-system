package com.studymatching.auth.entity;

import com.studymatching.member.entity.Member;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_sessions")
public class RefreshSession {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant expiresAt;
    private Instant revokedAt;

    protected RefreshSession() {}

    public RefreshSession(Member member, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID();
        this.member = member;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public UUID getId() { return id; }
    public Member getMember() { return member; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isActive(Instant now) { return revokedAt == null && expiresAt.isAfter(now); }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
}
