package com.example.arabicbot.config;

import com.example.arabicbot.handler.TelegramBotHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class BotConfiguration {

    @Bean
    public TelegramBotsApi telegramBotsApi(TelegramBotHandler botHandler) throws TelegramApiException {
        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(botHandler);
        // Инициализируем команды бота после регистрации
        botHandler.initializeBotCommands();
        return botsApi;
    }
}

