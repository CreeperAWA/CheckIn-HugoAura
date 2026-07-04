package indi.etern.checkIn.service.question;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ScoreRecalculationEventListener {
    
    private final ScoreRecalculationService recalculationService;
    private final Logger logger = LoggerFactory.getLogger(ScoreRecalculationEventListener.class);
    
    public ScoreRecalculationEventListener(ScoreRecalculationService recalculationService) {
        this.recalculationService = recalculationService;
    }
    
    @Async("scoreRecalculationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleScoreRecalculationEvent(ScoreRecalculationEvent event) {
        logger.info("Processing score recalculation event for question: {}, log: {}",
                event.questionId(), event.logId());
        try {
            recalculationService.executeRecalculation(event.questionId(), event.logId());
        } catch (Exception e) {
            logger.error("Error in score recalculation event handler: {}", e.getMessage(), e);
        }
    }
}
