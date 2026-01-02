package com.example.arabicbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_progress")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserProgress {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "current_lesson_id")
    private Long currentLessonId;

    @Column(name = "last_answered_question_id")
    private Long lastAnsweredQuestionId;
}

