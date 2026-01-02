package com.example.arabicbot.repository;

import com.example.arabicbot.entity.TestQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TestQuestionRepository extends JpaRepository<TestQuestion, Long> {
    List<TestQuestion> findByLessonIdOrderByIdAsc(Long lessonId);
}

