package com.studymatching.member.repository;

import com.studymatching.member.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<Member> findByUsername(String username);

    @Query("select m.id from Member m where m.username = :username")
    Optional<Long> findIdByUsername(@Param("username") String username);
}
