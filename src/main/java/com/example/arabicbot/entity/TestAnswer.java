package com.example.arabicbot.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "test_answers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private TestQuestion question;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;
}

