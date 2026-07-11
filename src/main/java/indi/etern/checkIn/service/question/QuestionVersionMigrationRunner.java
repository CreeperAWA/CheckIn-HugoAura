package indi.etern.checkIn.service.question;

import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.repositories.QuestionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class QuestionVersionMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(QuestionVersionMigrationRunner.class);

    private final QuestionRepository questionRepository;
    private final QuestionVersionService questionVersionService;

    public QuestionVersionMigrationRunner(QuestionRepository questionRepository,
                                          QuestionVersionService questionVersionService) {
        this.questionRepository = questionRepository;
        this.questionVersionService = questionVersionService;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void migrateQuestionsWithoutVersion() {
        List<Question> questions = questionRepository.findByVersionGroupIdIsNull();
        if (questions.isEmpty()) {
            return;
        }

        log.info("Found {} questions without version group, migrating...", questions.size());

        for (Question question : questions) {
            questionVersionService.initializeNewQuestionVersion(question, null);
        }

        questionRepository.saveAll(questions);
        log.info("Migrated {} questions with SHA-1 version numbers.", questions.size());
    }
}
