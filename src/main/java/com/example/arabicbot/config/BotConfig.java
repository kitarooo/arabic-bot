package com.example.arabicbot.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Configuration
@Data
public class BotConfig {
    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.username}")
    private String botUsername;

    @Value("${telegram.bot.admin.ids:}")
    private String adminIds;

    public List<Long> getAdminIdsList() {
        if (adminIds == null || adminIds.trim().isEmpty()) {
            return List.of();
        }
        return Arrays.stream(adminIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
    }

    public boolean isAdmin(Long userId) {
        return getAdminIdsList().contains(userId);
    }
}

