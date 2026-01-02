package com.example.arabicbot.service;

import com.example.arabicbot.entity.Lesson;
import com.example.arabicbot.entity.TestAnswer;
import com.example.arabicbot.entity.TestQuestion;
import com.example.arabicbot.repository.LessonRepository;
import com.example.arabicbot.repository.TestAnswerRepository;
import com.example.arabicbot.repository.TestQuestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LessonService {
    private final LessonRepository lessonRepository;
    private final TestQuestionRepository testQuestionRepository;
    private final TestAnswerRepository testAnswerRepository;

    public List<Lesson> getAllLessons() {
        return lessonRepository.findAllByOrderByIdAsc();
    }

    public Optional<Lesson> getLessonById(Long id) {
        return lessonRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<TestQuestion> getFirstQuestionForLesson(Long lessonId) {
        List<TestQuestion> questions = testQuestionRepository.findByLessonIdOrderByIdAsc(lessonId);
        return questions.isEmpty() ? Optional.empty() : Optional.of(questions.get(0));
    }

    @Transactional(readOnly = true)
    public Optional<TestQuestion> getQuestionById(Long questionId) {
        return testQuestionRepository.findById(questionId);
    }

    public Optional<Lesson> getNextLesson(Long currentLessonId) {
        List<Lesson> allLessons = getAllLessons();
        for (int i = 0; i < allLessons.size(); i++) {
            if (allLessons.get(i).getId().equals(currentLessonId) && i < allLessons.size() - 1) {
                return Optional.of(allLessons.get(i + 1));
            }
        }
        return Optional.empty();
    }

    @Transactional
    public Lesson createLessonWithQuestion(LessonCreationState state) {
        // Создаем урок
        Lesson lesson = new Lesson();
        lesson.setTitle(state.getTitle());
        lesson.setDescription(state.getDescription());
        lesson.setVideoFileId(state.getVideoFileId());
        lesson = lessonRepository.save(lesson);

        // Создаем 3 вопроса
        for (QuestionData questionData : state.getQuestions()) {
            // Создаем вопрос
            TestQuestion question = new TestQuestion();
            question.setLesson(lesson);
            question.setQuestion(questionData.getQuestion());
            question = testQuestionRepository.save(question);

            // Создаем 4 ответа
            List<String> answers = questionData.getAnswers();
            int correctIndex = questionData.getCorrectAnswerIndex() - 1; // Конвертируем 1-4 в 0-3

            for (int i = 0; i < answers.size(); i++) {
                TestAnswer answer = new TestAnswer();
                answer.setQuestion(question);
                answer.setText(answers.get(i));
                answer.setIsCorrect(i == correctIndex);
                testAnswerRepository.save(answer);
            }
        }

        return lesson;
    }

    @Transactional(readOnly = true)
    public Optional<TestQuestion> getNextQuestionForLesson(Long lessonId, Long currentQuestionId) {
        List<TestQuestion> questions = testQuestionRepository.findByLessonIdOrderByIdAsc(lessonId);
        for (int i = 0; i < questions.size(); i++) {
            if (questions.get(i).getId().equals(currentQuestionId) && i < questions.size() - 1) {
                return Optional.of(questions.get(i + 1));
            }
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public List<TestQuestion> getAllQuestionsForLesson(Long lessonId) {
        return testQuestionRepository.findByLessonIdOrderByIdAsc(lessonId);
    }

    @Transactional
    public void updateLessonTitle(Long lessonId, String title) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Урок не найден"));
        lesson.setTitle(title);
        lessonRepository.save(lesson);
    }

    @Transactional
    public void updateLessonVideo(Long lessonId, String videoFileId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Урок не найден"));
        lesson.setVideoFileId(videoFileId);
        lessonRepository.save(lesson);
    }

    @Transactional
    public void updateLessonDescription(Long lessonId, String description) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Урок не найден"));
        lesson.setDescription(description);
        lessonRepository.save(lesson);
    }

    @Transactional
    public void updateAnswerText(Long answerId, String newText) {
        TestAnswer answer = testAnswerRepository.findById(answerId)
                .orElseThrow(() -> new RuntimeException("Ответ не найден"));
        answer.setText(newText);
        testAnswerRepository.save(answer);
    }

    @Transactional
    public void updateCorrectAnswer(Long questionId, int correctAnswerIndex) {
        List<TestAnswer> answers = testAnswerRepository.findByQuestionIdOrderByIdAsc(questionId);
        if (correctAnswerIndex < 1 || correctAnswerIndex > answers.size()) {
            throw new RuntimeException("Неверный индекс правильного ответа");
        }
        
        // Сбрасываем все ответы как неправильные
        for (TestAnswer answer : answers) {
            answer.setIsCorrect(false);
            testAnswerRepository.save(answer);
        }
        
        // Устанавливаем правильный ответ (индекс 1-4, конвертируем в 0-3)
        answers.get(correctAnswerIndex - 1).setIsCorrect(true);
        testAnswerRepository.save(answers.get(correctAnswerIndex - 1));
    }

    @Transactional(readOnly = true)
    public List<TestAnswer> getAnswersForQuestion(Long questionId) {
        return testAnswerRepository.findByQuestionIdOrderByIdAsc(questionId);
    }

    @Transactional
    public void deleteLesson(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> new RuntimeException("Урок не найден"));
        lessonRepository.delete(lesson);
    }
}

