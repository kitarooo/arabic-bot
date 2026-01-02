package com.example.arabicbot.service;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LessonCreationState {
    private String title;
    private String description;
    private String videoFileId;
    private List<QuestionData> questions = new ArrayList<>();
    private int currentQuestionIndex = 0;

    public QuestionData getCurrentQuestion() {
        while (questions.size() <= currentQuestionIndex) {
            questions.add(new QuestionData());
        }
        return questions.get(currentQuestionIndex);
    }

    public boolean isComplete() {
        return title != null && !title.trim().isEmpty() &&
               description != null && !description.trim().isEmpty() &&
               videoFileId != null && !videoFileId.trim().isEmpty() &&
               questions.size() == 3 &&
               questions.stream().allMatch(QuestionData::isComplete);
    }

    public void reset() {
        title = null;
        description = null;
        videoFileId = null;
        questions.clear();
        currentQuestionIndex = 0;
    }
}

