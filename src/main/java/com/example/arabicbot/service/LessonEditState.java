package com.example.arabicbot.service;

import lombok.Data;

@Data
public class LessonEditState {
    private Long lessonId;
    private EditStep currentStep = EditStep.SELECT_LESSON;
    private EditAction selectedAction;
    private Long selectedQuestionId;
    private int currentAnswerIndex = 0; // Для редактирования ответов (0-3)

    public enum EditStep {
        SELECT_LESSON,
        SELECT_ACTION,
        EDIT_TITLE,
        EDIT_VIDEO,
        EDIT_DESCRIPTION,
        SELECT_QUESTION_FOR_ANSWERS,
        EDIT_ANSWER,
        SELECT_QUESTION_FOR_CORRECT,
        SELECT_CORRECT_ANSWER
    }

    public enum EditAction {
        CHANGE_TITLE,
        CHANGE_VIDEO,
        CHANGE_DESCRIPTION,
        EDIT_ANSWERS,
        CHANGE_CORRECT_ANSWER
    }

    public void reset() {
        lessonId = null;
        currentStep = EditStep.SELECT_LESSON;
        selectedAction = null;
        selectedQuestionId = null;
        currentAnswerIndex = 0;
    }
}

