package com.example.arabicbot.service;

import com.example.arabicbot.entity.UserProgress;
import com.example.arabicbot.repository.UserProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserProgressService {
    private final UserProgressRepository userProgressRepository;

    public Optional<UserProgress> getUserProgress(Long userId) {
        return userProgressRepository.findByUserId(userId);
    }

    @Transactional
    public UserProgress saveOrUpdateProgress(Long userId, Long lessonId, Long questionId) {
        Optional<UserProgress> existing = userProgressRepository.findByUserId(userId);
        
        if (existing.isPresent()) {
            UserProgress progress = existing.get();
            progress.setCurrentLessonId(lessonId);
            progress.setLastAnsweredQuestionId(questionId);
            return userProgressRepository.save(progress);
        } else {
            UserProgress progress = new UserProgress();
            progress.setUserId(userId);
            progress.setCurrentLessonId(lessonId);
            progress.setLastAnsweredQuestionId(questionId);
            return userProgressRepository.save(progress);
        }
    }

    @Transactional
    public void updateCurrentLesson(Long userId, Long lessonId) {
        Optional<UserProgress> existing = userProgressRepository.findByUserId(userId);
        
        if (existing.isPresent()) {
            UserProgress progress = existing.get();
            progress.setCurrentLessonId(lessonId);
            progress.setLastAnsweredQuestionId(null);
            userProgressRepository.save(progress);
        } else {
            UserProgress progress = new UserProgress();
            progress.setUserId(userId);
            progress.setCurrentLessonId(lessonId);
            progress.setLastAnsweredQuestionId(null);
            userProgressRepository.save(progress);
        }
    }
}

