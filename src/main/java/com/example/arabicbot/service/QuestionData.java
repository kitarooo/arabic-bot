package com.example.arabicbot.service;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QuestionData {
    private String question;
    private List<String> answers = new ArrayList<>();
    private Integer correctAnswerIndex;

    public void addAnswer(String answer) {
        answers.add(answer);
    }

    public boolean isComplete() {
        return question != null && !question.trim().isEmpty() &&
               answers.size() == 4 &&
               correctAnswerIndex != null && correctAnswerIndex >= 1 && correctAnswerIndex <= 4;
    }

    public void reset() {
        
        question = null;
        answers.clear();
        correctAnswerIndex = null;
    }
}

