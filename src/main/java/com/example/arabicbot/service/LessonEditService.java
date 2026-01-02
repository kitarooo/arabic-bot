package com.example.arabicbot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class LessonEditService {
    private final Map<Long, LessonEditState> userStates = new ConcurrentHashMap<>();

    public LessonEditState getOrCreateState(Long userId) {
        return userStates.computeIfAbsent(userId, k -> new LessonEditState());
    }

    public LessonEditState getState(Long userId) {
        return userStates.get(userId);
    }

    public void clearState(Long userId) {
        userStates.remove(userId);
    }

    public boolean hasActiveEdit(Long userId) {
        return userStates.containsKey(userId);
    }
}

