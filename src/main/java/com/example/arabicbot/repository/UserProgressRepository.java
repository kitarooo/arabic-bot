package com.example.arabicbot.repository;

import com.example.arabicbot.entity.UserProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserProgressRepository extends JpaRepository<UserProgress, Long> {
    Optional<UserProgress> findByUserId(Long userId);
}

