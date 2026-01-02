package com.example.arabicbot.repository;

import com.example.arabicbot.entity.TestAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestAnswerRepository extends JpaRepository<TestAnswer, Long> {
    List<TestAnswer> findByQuestionIdOrderByIdAsc(Long questionId);
}

