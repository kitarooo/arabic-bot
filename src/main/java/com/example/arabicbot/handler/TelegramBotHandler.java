package com.example.arabicbot.handler;

import com.example.arabicbot.config.BotConfig;
import com.example.arabicbot.entity.Lesson;
import com.example.arabicbot.entity.TestAnswer;
import com.example.arabicbot.entity.TestQuestion;
import com.example.arabicbot.entity.UserProgress;
import com.example.arabicbot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramBotHandler extends TelegramLongPollingBot {

    private final BotConfig botConfig;
    private final LessonService lessonService;
    private final UserProgressService userProgressService;
    private final LessonCreationService lessonCreationService;
    private final LessonEditService lessonEditService;

    @Override
    public String getBotUsername() {
        return botConfig.getBotUsername();
    }

    @Override
    public String getBotToken() {
        return botConfig.getBotToken();
    }

    public void initializeBotCommands() {
        try {
            List<BotCommand> commands = new ArrayList<>();
            commands.add(new BotCommand("start", "Начать тест"));
            commands.add(new BotCommand("menu", "Меню"));
            commands.add(new BotCommand("help", "Помощь"));
            
            // Добавляем админские команды
            commands.add(new BotCommand("admin", "Админ-меню"));
            
            SetMyCommands setMyCommands = new SetMyCommands();
            setMyCommands.setCommands(commands);
            setMyCommands.setScope(new BotCommandScopeDefault());
            execute(setMyCommands);
            
            log.info("Bot commands initialized successfully");
        } catch (TelegramApiException e) {
            log.error("Error initializing bot commands", e);
        }
    }

    private ReplyKeyboardMarkup createMainKeyboard(boolean isAdmin) {
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setOneTimeKeyboard(false);
        keyboardMarkup.setSelective(true);

        List<KeyboardRow> keyboard = new ArrayList<>();

        // Первая строка
        KeyboardRow row1 = new KeyboardRow();
        row1.add(new KeyboardButton("📚 Начать тест"));
        keyboard.add(row1);

        // Вторая строка
        KeyboardRow row2 = new KeyboardRow();
        row2.add(new KeyboardButton("📊 Профиль"));
        row2.add(new KeyboardButton("❓ Помощь"));
        keyboard.add(row2);

        // Третья строка для админов
        if (isAdmin) {
            KeyboardRow row3 = new KeyboardRow();
            row3.add(new KeyboardButton("🔧 Админ-меню"));
            keyboard.add(row3);
        }

        keyboardMarkup.setKeyboard(keyboard);
        return keyboardMarkup;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage()) {
            if (update.getMessage().hasText()) {
                handleMessage(update);
            } else if (update.getMessage().hasVideo()) {
                handleVideo(update);
            }
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update);
        }
    }

    private void handleMessage(Update update) {
        String messageText = update.getMessage().getText();
        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();

        // Проверяем, находится ли пользователь в процессе создания урока
        if (lessonCreationService.hasActiveCreation(userId)) {
            handleLessonCreationStep(update, userId, chatId, messageText);
            return;
        }

        // Проверяем, находится ли пользователь в процессе редактирования урока
        if (lessonEditService.hasActiveEdit(userId)) {
            handleLessonEditStep(update, userId, chatId, messageText);
            return;
        }

        // Обработка команд и кнопок клавиатуры
        if (messageText.equals("/start") || messageText.equals("📚 Начать тест")) {
            // Для обычных пользователей показываем первый урок сразу
            if (!botConfig.isAdmin(userId)) {
                List<Lesson> lessons = lessonService.getAllLessons();
                if (!lessons.isEmpty()) {
                    sendLesson(chatId, lessons.get(0).getId(), userId);
                } else {
                    sendMessageWithKeyboard(chatId, "Уроки пока не добавлены.", userId);
                }
            } else {
                // Для админов показываем админ-меню
                sendAdminMenu(chatId);
            }
        } else if (messageText.equals("/menu") || messageText.equals("🔧 Админ-меню")) {
            if (botConfig.isAdmin(userId)) {
                sendAdminMenu(chatId);
            } else {
                sendMessage(chatId, "❌ У вас нет прав доступа к админ-меню.");
            }
        } else if (messageText.equals("/help") || messageText.equals("❓ Помощь")) {
            sendHelpMessage(chatId, userId);
        } else if (messageText.equals("📊 Профиль")) {
            sendProfileMessage(chatId, userId);
        } else if (messageText.equals("/lesson_create")) {
            handleLessonCreateCommand(chatId, userId);
        } else if (messageText.equals("/изменить существующий") || messageText.equals("/edit_lesson")) {
            handleEditLessonCommand(chatId, userId);
        } else if (messageText.equals("/admin") || messageText.equals("/меню")) {
            if (botConfig.isAdmin(userId)) {
                sendAdminMenu(chatId);
            } else {
                sendMessage(chatId, "❌ У вас нет прав доступа к админ-меню.");
            }
        } else {
            sendMessage(chatId, "Используйте кнопки меню или команды для навигации");
        }
    }

    private void handleVideo(Update update) {
        Long chatId = update.getMessage().getChatId();
        Long userId = update.getMessage().getFrom().getId();

        // Проверяем, находится ли пользователь в процессе создания урока
        if (lessonCreationService.hasActiveCreation(userId)) {
            LessonCreationState state = lessonCreationService.getState(userId);
            
            // Проверяем, ожидаем ли мы видео
            if (state.getTitle() != null && state.getDescription() != null && state.getVideoFileId() == null) {
                String videoFileId = update.getMessage().getVideo().getFileId();
                state.setVideoFileId(videoFileId);
                
                // Переходим к следующему шагу - первый вопрос
                sendMessage(chatId, "Вопрос 1/3\nВопрос?");
            } else {
                sendMessage(chatId, "Сначала заполните название и описание урока.");
            }
            return;
        }

        // Проверяем, находится ли пользователь в процессе редактирования урока
        if (lessonEditService.hasActiveEdit(userId)) {
            LessonEditState editState = lessonEditService.getState(userId);
            
            // Проверяем, ожидаем ли мы видео для редактирования
            if (editState.getCurrentStep() == LessonEditState.EditStep.EDIT_VIDEO && editState.getLessonId() != null) {
                        String videoFileId = update.getMessage().getVideo().getFileId();
                        lessonService.updateLessonVideo(editState.getLessonId(), videoFileId);
                        sendMessage(chatId, "✅ Видео успешно обновлено!");
                        lessonEditService.clearState(userId);
                        sendAdminMenu(chatId);
            }
        }
    }

    private void handleLessonCreateCommand(Long chatId, Long userId) {
        // Проверяем права админа
        if (!botConfig.isAdmin(userId)) {
            sendMessage(chatId, "❌ У вас нет прав для создания уроков.");
            return;
        }

        // Начинаем процесс создания урока
        LessonCreationState state = lessonCreationService.getOrCreateState(userId);
        state.reset();
        
        sendMessage(chatId, "Создание нового урока\n\nНазвание?");
    }

    private void handleLessonCreationStep(Update update, Long userId, Long chatId, String messageText) {
        LessonCreationState state = lessonCreationService.getState(userId);
        
        if (state == null) {
            sendMessage(chatId, "Сессия создания урока истекла. Начните заново с /lesson_create");
            return;
        }

        try {
            QuestionData currentQuestion = state.getCurrentQuestion();
            
            // Определяем текущий шаг и обрабатываем ввод
            if (state.getTitle() == null) {
                // Шаг 1: Название
                state.setTitle(messageText);
                sendMessage(chatId, "Описание?");
                
            } else if (state.getDescription() == null) {
                // Шаг 2: Описание
                state.setDescription(messageText);
                sendMessage(chatId, "Отправь видео");
                
            } else if (state.getVideoFileId() == null) {
                // Шаг 3: Видео (обрабатывается в handleVideo)
                sendMessage(chatId, "Пожалуйста, отправьте видео файл.");
                
            } else if (currentQuestion.getQuestion() == null) {
                // Шаг 4: Вопрос для текущего теста
                currentQuestion.setQuestion(messageText);
                int questionNum = state.getCurrentQuestionIndex() + 1;
                sendMessage(chatId, String.format("Вопрос %d/3\n4 варианта ответов? (можно ввести все через запятую или по одному)", questionNum));
                
            } else if (currentQuestion.getAnswers().size() < 4) {
                // Шаг 5: Варианты ответов
                // Проверяем, введены ли все ответы через запятую
                if (messageText.contains(",") && messageText.split(",").length >= 4) {
                    String[] answers = messageText.split(",");
                    for (int i = 0; i < 4; i++) {
                        currentQuestion.addAnswer(answers[i].trim());
                    }
                } else {
                    // Добавляем один ответ
                    currentQuestion.addAnswer(messageText);
                }
                
                if (currentQuestion.getAnswers().size() < 4) {
                    sendMessage(chatId, String.format("Добавлено ответов: %d/4. Введите следующий ответ:", currentQuestion.getAnswers().size()));
                } else {
                    sendMessage(chatId, "Какой правильный (1-4)?");
                }
                
            } else if (currentQuestion.getCorrectAnswerIndex() == null) {
                // Шаг 6: Правильный ответ
                try {
                    int answerIndex = Integer.parseInt(messageText.trim());
                    if (answerIndex < 1 || answerIndex > 4) {
                        sendMessage(chatId, "Пожалуйста, введите число от 1 до 4:");
                        return;
                    }
                    currentQuestion.setCorrectAnswerIndex(answerIndex);
                    
                    // Проверяем, все ли вопросы заполнены
                    if (state.getCurrentQuestionIndex() < 2) {
                        // Переходим к следующему вопросу
                        state.setCurrentQuestionIndex(state.getCurrentQuestionIndex() + 1);
                        int questionNum = state.getCurrentQuestionIndex() + 1;
                        sendMessage(chatId, String.format("Вопрос %d/3\nВопрос?", questionNum));
                    } else {
                        // Все вопросы заполнены, сохраняем урок
                        if (state.isComplete()) {
                            Lesson lesson = lessonService.createLessonWithQuestion(state);
                            sendMessage(chatId, String.format("✅ Урок \"%s\" успешно создан! (ID: %d)", 
                                    lesson.getTitle(), lesson.getId()));
                            lessonCreationService.clearState(userId);
                            // Возвращаем админа в меню
                            if (botConfig.isAdmin(userId)) {
                                sendAdminMenu(chatId);
                            }
                        } else {
                            sendMessage(chatId, "❌ Ошибка: не все данные заполнены. Начните заново с /lesson_create");
                            lessonCreationService.clearState(userId);
                        }
                    }
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Пожалуйста, введите число от 1 до 4:");
                }
            }
        } catch (Exception e) {
            log.error("Error handling lesson creation step", e);
            sendMessage(chatId, "Произошла ошибка. Попробуйте начать заново с /lesson_create");
            lessonCreationService.clearState(userId);
        }
    }

    private void handleCallbackQuery(Update update) {
        String callbackData = update.getCallbackQuery().getData();
        Long chatId = update.getCallbackQuery().getMessage().getChatId();
        Long userId = update.getCallbackQuery().getFrom().getId();
        Integer messageId = update.getCallbackQuery().getMessage().getMessageId();

        try {
            if (callbackData.startsWith("lesson:")) {
                // Выбор урока
                Long lessonId = Long.parseLong(callbackData.split(":")[1]);
                sendLesson(chatId, lessonId, userId);
            } else if (callbackData.startsWith("answer:")) {
                // Ответ на вопрос
                String[] parts = callbackData.split(":", 5); // Ограничиваем split до 5 частей
                Long lessonId = Long.parseLong(parts[1]);
                Long questionId = Long.parseLong(parts[2]);
                Long answerId = Long.parseLong(parts[3]);
                // parts[4] содержит порядок всех answerId через запятую
                String answerOrder = parts.length > 4 ? parts[4] : "";
                handleAnswer(chatId, userId, lessonId, questionId, answerId, messageId, answerOrder);
            } else if (callbackData.equals("next_lesson")) {
                // Следующий урок
                handleNextLesson(chatId, userId);
            } else if (callbackData.equals("choose_lesson")) {
                // Выбрать урок
                sendLessonsList(chatId);
            } else if (callbackData.equals("answered")) {
                // Игнорируем нажатия на уже отвеченные вопросы
                return;
            } else if (callbackData.equals("edit_cancel")) {
                // Отмена редактирования
                LessonEditState state = lessonEditService.getState(userId);
                if (state != null) {
                    sendMessage(chatId, "❌ Изменения отменены.");
                    lessonEditService.clearState(userId);
                }
            } else if (callbackData.equals("edit_skip")) {
                // Пропуск редактирования ответа
                LessonEditState state = lessonEditService.getState(userId);
                if (state != null && state.getCurrentStep() == LessonEditState.EditStep.EDIT_ANSWER) {
                    state.setCurrentAnswerIndex(state.getCurrentAnswerIndex() + 1);
                    List<TestAnswer> answers = lessonService.getAnswersForQuestion(state.getSelectedQuestionId());
                    if (state.getCurrentAnswerIndex() < answers.size()) {
                        sendNextAnswerEdit(chatId, state, answers);
                    } else {
                        sendMessage(chatId, "✅ Редактирование ответов завершено!");
                        lessonEditService.clearState(userId);
                        sendAdminMenu(chatId);
                    }
                }
            } else if (callbackData.startsWith("edit_lesson:")) {
                // Выбор урока для редактирования
                Long lessonId = Long.parseLong(callbackData.split(":")[1]);
                LessonEditState state = lessonEditService.getOrCreateState(userId);
                state.setLessonId(lessonId);
                state.setCurrentStep(LessonEditState.EditStep.SELECT_ACTION);
                sendActionSelection(chatId, lessonId);
            } else if (callbackData.startsWith("edit_action:")) {
                // Выбор действия редактирования
                String[] parts = callbackData.split(":");
                String action = parts[1];
                Long lessonId = Long.parseLong(parts[2]);
                LessonEditState state = lessonEditService.getOrCreateState(userId);
                state.setLessonId(lessonId);

                switch (action) {
                    case "title":
                        state.setCurrentStep(LessonEditState.EditStep.EDIT_TITLE);
                        state.setSelectedAction(LessonEditState.EditAction.CHANGE_TITLE);
                        Optional<Lesson> lessonOptTitle = lessonService.getLessonById(lessonId);
                        if (lessonOptTitle.isPresent()) {
                            String currentTitle = lessonOptTitle.get().getTitle();
                            sendMessageWithCancelButton(chatId, 
                                "Текущее название:\n" + currentTitle + 
                                "\n\nВведите новое название:");
                        }
                        break;
                    case "video":
                        state.setCurrentStep(LessonEditState.EditStep.EDIT_VIDEO);
                        state.setSelectedAction(LessonEditState.EditAction.CHANGE_VIDEO);
                        sendMessageWithCancelButton(chatId, "Отправьте новое видео для урока:");
                        break;
                    case "description":
                        state.setCurrentStep(LessonEditState.EditStep.EDIT_DESCRIPTION);
                        state.setSelectedAction(LessonEditState.EditAction.CHANGE_DESCRIPTION);
                        Optional<Lesson> lessonOpt = lessonService.getLessonById(lessonId);
                        if (lessonOpt.isPresent()) {
                            String currentDesc = lessonOpt.get().getDescription();
                            sendMessageWithCancelButton(chatId, 
                                "Текущее описание:\n" + (currentDesc != null ? currentDesc : "(нет описания)") +
                                "\n\nВведите новое описание:");
                        }
                        break;
                    case "answers":
                        state.setCurrentStep(LessonEditState.EditStep.SELECT_QUESTION_FOR_ANSWERS);
                        sendQuestionSelectionForAnswers(chatId, lessonId);
                        break;
                    case "correct":
                        state.setCurrentStep(LessonEditState.EditStep.SELECT_QUESTION_FOR_CORRECT);
                        sendQuestionSelectionForCorrect(chatId, lessonId);
                        break;
                }
            } else if (callbackData.startsWith("edit_question_answers:")) {
                // Выбор вопроса для редактирования ответов
                Long questionId = Long.parseLong(callbackData.split(":")[1]);
                LessonEditState state = lessonEditService.getOrCreateState(userId);
                state.setSelectedQuestionId(questionId);
                state.setCurrentStep(LessonEditState.EditStep.EDIT_ANSWER);
                state.setCurrentAnswerIndex(0);

                Optional<TestQuestion> questionOpt = lessonService.getQuestionById(questionId);
                if (questionOpt.isPresent()) {
                    List<TestAnswer> answers = lessonService.getAnswersForQuestion(questionId);
                    sendMessage(chatId, "Вопрос:\n" + questionOpt.get().getQuestion());
                    if (!answers.isEmpty()) {
                        sendNextAnswerEdit(chatId, state, answers);
                    }
                }
            } else if (callbackData.startsWith("edit_question_correct:")) {
                // Выбор вопроса для изменения правильного ответа
                Long questionId = Long.parseLong(callbackData.split(":")[1]);
                LessonEditState state = lessonEditService.getOrCreateState(userId);
                state.setSelectedQuestionId(questionId);
                state.setCurrentStep(LessonEditState.EditStep.SELECT_CORRECT_ANSWER);
                sendCorrectAnswerSelection(chatId, questionId);
            } else if (callbackData.startsWith("edit_correct:")) {
                // Выбор нового правильного ответа
                String[] parts = callbackData.split(":");
                Long questionId = Long.parseLong(parts[1]);
                int correctIndex = Integer.parseInt(parts[2]);
                lessonService.updateCorrectAnswer(questionId, correctIndex);
                sendMessage(chatId, "✅ Правильный ответ успешно обновлен!");
                lessonEditService.clearState(userId);
                sendAdminMenu(chatId);
            } else if (callbackData.equals("admin_menu")) {
                // Возврат в админ-меню
                sendAdminMenu(chatId);
            } else if (callbackData.equals("admin_create_lesson")) {
                // Создание урока из меню
                handleLessonCreateCommand(chatId, userId);
            } else if (callbackData.equals("admin_edit_lesson")) {
                // Редактирование урока из меню
                handleEditLessonCommand(chatId, userId);
            } else if (callbackData.equals("admin_delete_lesson")) {
                // Удаление урока из меню
                sendLessonsListForDelete(chatId);
            } else if (callbackData.equals("admin_list_lessons")) {
                // Список уроков из меню
                sendLessonsListWithMenu(chatId);
            } else if (callbackData.startsWith("delete_lesson:")) {
                // Выбор урока для удаления
                Long lessonId = Long.parseLong(callbackData.split(":")[1]);
                sendDeleteConfirmation(chatId, lessonId);
            } else if (callbackData.startsWith("confirm_delete:")) {
                // Подтверждение удаления
                Long lessonId = Long.parseLong(callbackData.split(":")[1]);
                try {
                    Optional<Lesson> lessonOpt = lessonService.getLessonById(lessonId);
                    if (lessonOpt.isPresent()) {
                        String lessonTitle = lessonOpt.get().getTitle();
                        lessonService.deleteLesson(lessonId);
                        sendMessage(chatId, "✅ Урок \"" + lessonTitle + "\" успешно удален!");
                        sendAdminMenu(chatId);
                    } else {
                        sendMessage(chatId, "❌ Урок не найден.");
                    }
                } catch (Exception e) {
                    log.error("Error deleting lesson", e);
                    sendMessage(chatId, "❌ Произошла ошибка при удалении урока.");
                }
            } else if (callbackData.startsWith("cancel_delete:")) {
                // Отмена удаления
                sendMessage(chatId, "❌ Удаление отменено.");
                sendAdminMenu(chatId);
            }
        } catch (Exception e) {
            log.error("Error handling callback: {}", callbackData, e);
            sendMessage(chatId, "Произошла ошибка. Попробуйте еще раз.");
        }
    }

    private void sendLessonsList(Long chatId) {
        List<Lesson> lessons = lessonService.getAllLessons();

        if (lessons.isEmpty()) {
            sendMessage(chatId, "Уроки пока не добавлены.");
            return;
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Lesson lesson : lessons) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(lesson.getTitle());
            button.setCallbackData("lesson:" + lesson.getId());
            rows.add(Collections.singletonList(button));
        }

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("📚 Выберите урок:");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending lessons list", e);
        }
    }

    private void sendLessonsListWithMenu(Long chatId) {
        List<Lesson> lessons = lessonService.getAllLessons();

        if (lessons.isEmpty()) {
            sendMessage(chatId, "Уроки пока не добавлены.");
            sendAdminMenu(chatId);
            return;
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Lesson lesson : lessons) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(lesson.getTitle());
            button.setCallbackData("lesson:" + lesson.getId());
            rows.add(Collections.singletonList(button));
        }

        // Кнопка возврата в меню
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("◀️ Назад в меню");
        backButton.setCallbackData("admin_menu");
        rows.add(Collections.singletonList(backButton));

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("📚 Выберите урок:");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending lessons list with menu", e);
        }
    }

    private void sendLesson(Long chatId, Long lessonId, Long userId) {
        Optional<Lesson> lessonOpt = lessonService.getLessonById(lessonId);

        if (lessonOpt.isEmpty()) {
            sendMessage(chatId, "Урок не найден.");
            return;
        }

        Lesson lesson = lessonOpt.get();

        // Обновляем прогресс пользователя
        userProgressService.updateCurrentLesson(userId, lessonId);

        // Отправляем видео
        sendVideo(chatId, lesson.getVideoFileId());

        // Отправляем описание
        String description = "📝 " + lesson.getTitle() + "\n\n" +
                           (lesson.getDescription() != null ? lesson.getDescription() : "");
        sendMessage(chatId, description);

        // Отправляем первый вопрос теста
        Optional<TestQuestion> questionOpt = lessonService.getFirstQuestionForLesson(lessonId);

        if (questionOpt.isPresent()) {
            sendTestQuestion(chatId, questionOpt.get());
        } else {
            sendMessage(chatId, "Вопросы для этого урока пока не добавлены.");
        }
    }


    private void sendVideo(Long chatId, String videoFileId) {
        SendVideo video = new SendVideo();
        video.setChatId(chatId.toString());
        video.setVideo(new InputFile(videoFileId));

        try {
            execute(video);
        } catch (TelegramApiException e) {
            log.error("Error sending video", e);
        }
    }


    private void sendTestQuestion(Long chatId, TestQuestion question) {
        if (question.getAnswers() == null || question.getAnswers().size() < 4) {
            sendMessage(chatId, "Для вопроса должно быть 4 варианта ответа.");
            return;
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Перемешиваем ответы для случайного порядка
        List<TestAnswer> shuffledAnswers = new ArrayList<>(question.getAnswers());
        Collections.shuffle(shuffledAnswers);

        // Сохраняем порядок всех answerId для последующего обновления сообщения
        StringBuilder orderBuilder = new StringBuilder();
        for (TestAnswer answer : shuffledAnswers) {
            if (orderBuilder.length() > 0) {
                orderBuilder.append(",");
            }
            orderBuilder.append(answer.getId());
        }
        String answerOrder = orderBuilder.toString();

        // Создаем кнопки с ответами (A, B, C, D)
        char label = 'A';
        for (TestAnswer answer : shuffledAnswers) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(label + ". " + answer.getText());
            // Формат: answer:lessonId:questionId:answerId:order (order содержит все answerId через запятую)
            button.setCallbackData("answer:" + question.getLesson().getId() + ":" + 
                                 question.getId() + ":" + answer.getId() + ":" + answerOrder);
            rows.add(Collections.singletonList(button));
            label++;
        }

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("❓ " + question.getQuestion());
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending test question", e);
        }
    }

    private void handleAnswer(Long chatId, Long userId, Long lessonId, Long questionId, 
                             Long answerId, Integer messageId, String answerOrder) {
        Optional<TestQuestion> questionOpt = lessonService.getQuestionById(questionId);
        if (questionOpt.isEmpty()) {
            sendMessage(chatId, "Вопрос не найден.");
            return;
        }

        TestQuestion question = questionOpt.get();
        
        // Находим выбранный ответ и правильный ответ
        TestAnswer selectedAnswer = null;
        TestAnswer correctAnswer = null;
        
        for (TestAnswer answer : question.getAnswers()) {
            if (answer.getId().equals(answerId)) {
                selectedAnswer = answer;
            }
            if (answer.getIsCorrect()) {
                correctAnswer = answer;
            }
        }

        if (selectedAnswer == null) {
            sendMessage(chatId, "Ответ не найден.");
            return;
        }

        // Сохраняем прогресс
        userProgressService.saveOrUpdateProgress(userId, lessonId, questionId);

        // Обновляем существующее сообщение с результатами
        updateQuestionMessageWithResults(chatId, messageId, question, selectedAnswer, correctAnswer, answerOrder);

        // Проверяем, есть ли следующий вопрос
        Optional<TestQuestion> nextQuestionOpt = lessonService.getNextQuestionForLesson(lessonId, questionId);
        
        if (nextQuestionOpt.isPresent()) {
            // Показываем следующий вопрос
            try {
                Thread.sleep(1000); // Небольшая задержка перед следующим вопросом
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sendTestQuestion(chatId, nextQuestionOpt.get());
        } else {
            // Все вопросы пройдены, автоматически переходим к следующему уроку
            try {
                Thread.sleep(1500); // Небольшая задержка перед следующим уроком
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            handleNextLesson(chatId, userId);
        }
    }

    private void updateQuestionMessageWithResults(Long chatId, Integer messageId, TestQuestion question,
                                                  TestAnswer selectedAnswer, TestAnswer correctAnswer, String answerOrder) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Восстанавливаем порядок ответов из answerOrder
        List<TestAnswer> orderedAnswers = new ArrayList<>();
        if (answerOrder != null && !answerOrder.isEmpty()) {
            String[] answerIds = answerOrder.split(",");
            // Создаем Map для быстрого поиска ответов по ID
            Map<Long, TestAnswer> answerMap = new HashMap<>();
            for (TestAnswer answer : question.getAnswers()) {
                answerMap.put(answer.getId(), answer);
            }
            // Восстанавливаем порядок
            for (String answerIdStr : answerIds) {
                Long answerId = Long.parseLong(answerIdStr);
                TestAnswer answer = answerMap.get(answerId);
                if (answer != null) {
                    orderedAnswers.add(answer);
                }
            }
        } else {
            // Если порядок не сохранен, используем исходный порядок
            orderedAnswers = question.getAnswers();
        }

        // Создаем кнопки с результатами в том же порядке, что и в исходном сообщении
        char label = 'A';
        for (TestAnswer answer : orderedAnswers) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            String prefix = "";
            
            if (answer.getId().equals(correctAnswer.getId())) {
                prefix = "✅ ";
            } else if (answer.getId().equals(selectedAnswer.getId()) && !selectedAnswer.getIsCorrect()) {
                prefix = "❌ ";
            }
            
            button.setText(prefix + label + ". " + answer.getText());
            button.setCallbackData("answered"); // Отключаем кнопки
            rows.add(Collections.singletonList(button));
            label++;
        }

        keyboard.setKeyboard(rows);

        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId.toString());
        editMessage.setMessageId(messageId);
        editMessage.setText("❓ " + question.getQuestion() + 
                          (selectedAnswer.getIsCorrect() ? "\n\n✅ Верно!" : "\n\n❌ Неверно"));
        editMessage.setReplyMarkup(keyboard);

        try {
            execute(editMessage);
        } catch (TelegramApiException e) {
            log.error("Error updating message with results", e);
        }
    }

    private void sendNavigationButtons(Long chatId, Long currentLessonId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // Кнопка "Следующий урок"
        InlineKeyboardButton nextButton = new InlineKeyboardButton();
        nextButton.setText("➡️ Следующий урок");
        nextButton.setCallbackData("next_lesson");
        rows.add(Collections.singletonList(nextButton));

        // Кнопка "Выбрать урок"
        InlineKeyboardButton chooseButton = new InlineKeyboardButton();
        chooseButton.setText("📚 Выбрать урок");
        chooseButton.setCallbackData("choose_lesson");
        rows.add(Collections.singletonList(chooseButton));

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Что дальше?");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending navigation buttons", e);
        }
    }

    private void handleNextLesson(Long chatId, Long userId) {
        Optional<UserProgress> progressOpt =
                userProgressService.getUserProgress(userId);

        Long currentLessonId = progressOpt
                .map(UserProgress::getCurrentLessonId)
                .orElse(null);

        if (currentLessonId == null) {
            sendLessonsList(chatId);
            return;
        }

        Optional<Lesson> nextLessonOpt = lessonService.getNextLesson(currentLessonId);
        
        if (nextLessonOpt.isPresent()) {
            sendLesson(chatId, nextLessonOpt.get().getId(), userId);
        } else {
            sendMessage(chatId, "🎉 Поздравляем! Вы прошли все уроки!");
            sendLessonsList(chatId);
        }
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending message", e);
        }
    }

    private void sendMessageWithKeyboard(Long chatId, String text, Long userId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(createMainKeyboard(botConfig.isAdmin(userId)));

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending message with keyboard", e);
        }
    }

    private void sendHelpMessage(Long chatId, Long userId) {
        StringBuilder helpText = new StringBuilder();
        helpText.append("📖 Помощь\n\n");
        helpText.append("Доступные команды:\n");
        helpText.append("📚 Начать тест - начать прохождение уроков\n");
        helpText.append("📊 Профиль - посмотреть ваш прогресс\n");
        helpText.append("❓ Помощь - показать это сообщение\n");
        
        if (botConfig.isAdmin(userId)) {
            helpText.append("\n🔧 Админ-команды:\n");
            helpText.append("🔧 Админ-меню - открыть панель администратора\n");
        }
        
        sendMessageWithKeyboard(chatId, helpText.toString(), userId);
    }

    private void sendProfileMessage(Long chatId, Long userId) {
        Optional<UserProgress> progressOpt = userProgressService.getUserProgress(userId);
        
        StringBuilder profileText = new StringBuilder();
        profileText.append("📊 Ваш профиль\n\n");
        
        if (progressOpt.isPresent()) {
            UserProgress progress = progressOpt.get();
            if (progress.getCurrentLessonId() != null) {
                Optional<Lesson> lessonOpt = lessonService.getLessonById(progress.getCurrentLessonId());
                if (lessonOpt.isPresent()) {
                    profileText.append("Текущий урок: ").append(lessonOpt.get().getTitle()).append("\n");
                }
            }
            profileText.append("\nПродолжайте обучение!");
        } else {
            profileText.append("Вы еще не начали прохождение уроков.\n");
            profileText.append("Нажмите «📚 Начать тест» для начала!");
        }
        
        sendMessageWithKeyboard(chatId, profileText.toString(), userId);
    }

    // ========== Методы редактирования урока ==========

    private void handleEditLessonCommand(Long chatId, Long userId) {
        // Проверяем права админа
        if (!botConfig.isAdmin(userId)) {
            sendMessage(chatId, "❌ У вас нет прав для редактирования уроков.");
            return;
        }

        // Начинаем процесс редактирования
        LessonEditState state = lessonEditService.getOrCreateState(userId);
        state.reset();
        state.setCurrentStep(LessonEditState.EditStep.SELECT_LESSON);

        // Показываем список уроков для выбора
        sendLessonsListForEdit(chatId);
    }

    private void sendLessonsListForEdit(Long chatId) {
        List<Lesson> lessons = lessonService.getAllLessons();

        if (lessons.isEmpty()) {
            sendMessage(chatId, "Уроки пока не добавлены.");
            sendAdminMenu(chatId);
            return;
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Lesson lesson : lessons) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(lesson.getTitle());
            button.setCallbackData("edit_lesson:" + lesson.getId());
            rows.add(Collections.singletonList(button));
        }

        // Кнопка возврата в меню
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("◀️ Назад в меню");
        backButton.setCallbackData("admin_menu");
        rows.add(Collections.singletonList(backButton));

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Выберите урок для редактирования:");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending lessons list for edit", e);
        }
    }

    private void handleLessonEditStep(Update update, Long userId, Long chatId, String messageText) {
        LessonEditState state = lessonEditService.getState(userId);

        if (state == null) {
            sendMessage(chatId, "Сессия редактирования истекла. Начните заново с /изменить существующий");
            return;
        }

        // Обработка отмены и пропуска теперь через callback

        try {
            switch (state.getCurrentStep()) {
                case EDIT_TITLE:
                    lessonService.updateLessonTitle(state.getLessonId(), messageText);
                    sendMessage(chatId, "✅ Название успешно обновлено!");
                    lessonEditService.clearState(userId);
                    sendAdminMenu(chatId);
                    break;

                case EDIT_DESCRIPTION:
                    lessonService.updateLessonDescription(state.getLessonId(), messageText);
                    sendMessage(chatId, "✅ Описание успешно обновлено!");
                    lessonEditService.clearState(userId);
                    sendAdminMenu(chatId);
                    break;

                case EDIT_ANSWER:
                    List<TestAnswer> answers = lessonService.getAnswersForQuestion(state.getSelectedQuestionId());
                    if (state.getCurrentAnswerIndex() < answers.size()) {
                        TestAnswer answer = answers.get(state.getCurrentAnswerIndex());
                        lessonService.updateAnswerText(answer.getId(), messageText);
                        state.setCurrentAnswerIndex(state.getCurrentAnswerIndex() + 1);
                        
                        if (state.getCurrentAnswerIndex() < answers.size()) {
                            sendNextAnswerEdit(chatId, state, answers);
                        } else {
                            sendMessage(chatId, "✅ Редактирование ответов завершено!");
                            lessonEditService.clearState(userId);
                            sendAdminMenu(chatId);
                        }
                    }
                    break;

                default:
                    sendMessage(chatId, "Неожиданный шаг. Начните заново с /изменить существующий");
                    lessonEditService.clearState(userId);
            }
        } catch (Exception e) {
            log.error("Error handling lesson edit step", e);
            sendMessage(chatId, "Произошла ошибка. Попробуйте начать заново с /изменить существующий");
            lessonEditService.clearState(userId);
        }
    }

    private void sendNextAnswerEdit(Long chatId, LessonEditState state, List<TestAnswer> answers) {
        TestAnswer currentAnswer = answers.get(state.getCurrentAnswerIndex());
        sendMessageWithSkipButton(chatId, String.format("Ответ %d:\n%s\n\nВведите новый текст:",
                state.getCurrentAnswerIndex() + 1, currentAnswer.getText()));
    }

    private void sendActionSelection(Long chatId, Long lessonId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton titleButton = new InlineKeyboardButton();
        titleButton.setText("1️⃣ Поменять название");
        titleButton.setCallbackData("edit_action:title:" + lessonId);
        rows.add(Collections.singletonList(titleButton));

        InlineKeyboardButton videoButton = new InlineKeyboardButton();
        videoButton.setText("2️⃣ Поменять видео");
        videoButton.setCallbackData("edit_action:video:" + lessonId);
        rows.add(Collections.singletonList(videoButton));

        InlineKeyboardButton descButton = new InlineKeyboardButton();
        descButton.setText("3️⃣ Поменять описание");
        descButton.setCallbackData("edit_action:description:" + lessonId);
        rows.add(Collections.singletonList(descButton));

        InlineKeyboardButton answersButton = new InlineKeyboardButton();
        answersButton.setText("4️⃣ Редактировать варианты ответа");
        answersButton.setCallbackData("edit_action:answers:" + lessonId);
        rows.add(Collections.singletonList(answersButton));

        InlineKeyboardButton correctButton = new InlineKeyboardButton();
        correctButton.setText("5️⃣ Поменять правильный ответ");
        correctButton.setCallbackData("edit_action:correct:" + lessonId);
        rows.add(Collections.singletonList(correctButton));

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Выберите, что хотите изменить:");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending action selection", e);
        }
    }

    private void sendMessageWithCancelButton(Long chatId, String text) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData("edit_cancel");
        rows.add(Collections.singletonList(cancelButton));

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending message with cancel button", e);
        }
    }

    private void sendMessageWithSkipButton(Long chatId, String text) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton skipButton = new InlineKeyboardButton();
        skipButton.setText("⏭️ Пропустить");
        skipButton.setCallbackData("edit_skip");
        rows.add(Collections.singletonList(skipButton));

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending message with skip button", e);
        }
    }

    private void sendQuestionSelectionForAnswers(Long chatId, Long lessonId) {
        List<TestQuestion> questions = lessonService.getAllQuestionsForLesson(lessonId);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < questions.size(); i++) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText("Вопрос " + (i + 1));
            button.setCallbackData("edit_question_answers:" + questions.get(i).getId());
            rows.add(Collections.singletonList(button));
        }

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Выберите вопрос:");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending question selection for answers", e);
        }
    }

    private void sendQuestionSelectionForCorrect(Long chatId, Long lessonId) {
        List<TestQuestion> questions = lessonService.getAllQuestionsForLesson(lessonId);

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < questions.size(); i++) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText("Вопрос " + (i + 1));
            button.setCallbackData("edit_question_correct:" + questions.get(i).getId());
            rows.add(Collections.singletonList(button));
        }

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Выберите вопрос:");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending question selection for correct", e);
        }
    }

    private void sendCorrectAnswerSelection(Long chatId, Long questionId) {
        List<TestAnswer> answers = lessonService.getAnswersForQuestion(questionId);
        int currentCorrect = -1;
        for (int i = 0; i < answers.size(); i++) {
            if (answers.get(i).getIsCorrect()) {
                currentCorrect = i + 1;
                break;
            }
        }

        // Формируем текст с ответами и индексами
        StringBuilder messageText = new StringBuilder();
        messageText.append("Ответы:\n\n");
        
        for (int i = 0; i < answers.size(); i++) {
            String prefix = "";
            if (i + 1 == currentCorrect) {
                prefix = "✅ ";
            }
            messageText.append(prefix).append(i + 1).append(". ").append(answers.get(i).getText()).append("\n");
        }
        
        messageText.append("\nТекущий правильный ответ: ").append(currentCorrect);
        messageText.append("\n\nВыберите новый правильный ответ:");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (int i = 0; i < answers.size(); i++) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            String text = String.valueOf(i + 1);
            if (i + 1 == currentCorrect) {
                text += " ✅";
            }
            button.setText(text);
            button.setCallbackData("edit_correct:" + questionId + ":" + (i + 1));
            rows.add(Collections.singletonList(button));
        }

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(messageText.toString());
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending correct answer selection", e);
        }
    }

    // ========== Админ-меню ==========

    private void sendAdminMenu(Long chatId) {
        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton createButton = new InlineKeyboardButton();
        createButton.setText("➕ Создать урок");
        createButton.setCallbackData("admin_create_lesson");
        rows.add(Collections.singletonList(createButton));

        InlineKeyboardButton editButton = new InlineKeyboardButton();
        editButton.setText("✏️ Изменить урок");
        editButton.setCallbackData("admin_edit_lesson");
        rows.add(Collections.singletonList(editButton));

        InlineKeyboardButton deleteButton = new InlineKeyboardButton();
        deleteButton.setText("🗑️ Удалить урок");
        deleteButton.setCallbackData("admin_delete_lesson");
        rows.add(Collections.singletonList(deleteButton));

        InlineKeyboardButton listButton = new InlineKeyboardButton();
        listButton.setText("📚 Список уроков");
        listButton.setCallbackData("admin_list_lessons");
        rows.add(Collections.singletonList(listButton));

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("🔧 Админ-меню\n\nВыберите действие:");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending admin menu", e);
        }
    }

    private void sendLessonsListForDelete(Long chatId) {
        List<Lesson> lessons = lessonService.getAllLessons();

        if (lessons.isEmpty()) {
            sendMessage(chatId, "Уроки пока не добавлены.");
            sendAdminMenu(chatId);
            return;
        }

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Lesson lesson : lessons) {
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(lesson.getTitle());
            button.setCallbackData("delete_lesson:" + lesson.getId());
            rows.add(Collections.singletonList(button));
        }

        // Кнопка возврата в меню
        InlineKeyboardButton backButton = new InlineKeyboardButton();
        backButton.setText("◀️ Назад в меню");
        backButton.setCallbackData("admin_menu");
        rows.add(Collections.singletonList(backButton));

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("Выберите урок для удаления:");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending lessons list for delete", e);
        }
    }

    private void sendDeleteConfirmation(Long chatId, Long lessonId) {
        Optional<Lesson> lessonOpt = lessonService.getLessonById(lessonId);
        if (lessonOpt.isEmpty()) {
            sendMessage(chatId, "Урок не найден.");
            return;
        }

        Lesson lesson = lessonOpt.get();

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        InlineKeyboardButton confirmButton = new InlineKeyboardButton();
        confirmButton.setText("✅ Да, удалить");
        confirmButton.setCallbackData("confirm_delete:" + lessonId);
        rows.add(Collections.singletonList(confirmButton));

        InlineKeyboardButton cancelButton = new InlineKeyboardButton();
        cancelButton.setText("❌ Отмена");
        cancelButton.setCallbackData("cancel_delete:" + lessonId);
        rows.add(Collections.singletonList(cancelButton));

        keyboard.setKeyboard(rows);

        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText("⚠️ ВНИМАНИЕ!\n\n" +
                "Вы собираетесь удалить урок:\n" +
                "📝 " + lesson.getTitle() + "\n\n" +
                "Это действие нельзя отменить!\n\n" +
                "Вы уверены?");
        message.setReplyMarkup(keyboard);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error sending delete confirmation", e);
        }
    }
}

