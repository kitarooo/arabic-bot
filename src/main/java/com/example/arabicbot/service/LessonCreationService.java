package com.example.arabicbot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class LessonCreationService {
    private final Map<Long, LessonCreationState> userStates = new ConcurrentHashMap<>();

    public LessonCreationState getOrCreateState(Long userId) {
        return userStates.computeIfAbsent(userId, k -> new LessonCreationState());
    }

    public LessonCreationState getState(Long userId) {
        return userStates.get(userId);
    }

    public void clearState(Long userId) {
        userStates.remove(userId);
    }

    public boolean hasActiveCreation(Long userId) {
        return userStates.containsKey(userId);
    }
}

