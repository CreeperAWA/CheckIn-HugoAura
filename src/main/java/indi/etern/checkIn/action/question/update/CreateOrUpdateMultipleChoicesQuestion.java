package indi.etern.checkIn.action.question.update;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.dto.manage.question.IssueDTO;
import indi.etern.checkIn.dto.manage.question.MultipleChoicesQuestionDTO;
import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.service.dao.QuestionService;
import indi.etern.checkIn.service.dao.VerificationRuleService;
import indi.etern.checkIn.service.dao.verify.ValidationResult;
import indi.etern.checkIn.service.question.QuestionVersionService;
import indi.etern.checkIn.service.question.ScoreRecalculationService;
import indi.etern.checkIn.utils.QuestionCreateUtils;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

@Action(value = "createOrUpdateQuestion", exposed = false)
public class CreateOrUpdateMultipleChoicesQuestion extends BaseAction<CreateOrUpdateMultipleChoicesQuestion.Input, OutputData> {
    private final VerificationRuleService verificationRuleService;
    private final QuestionVersionService questionVersionService;
    private final ScoreRecalculationService scoreRecalculationService;
    
    public record Input(@Nonnull MultipleChoicesQuestionDTO multipleChoicesQuestionDTO) implements InputData {}
    public record SuccessOutput(Question question) implements OutputData {
        @Override
        public Result result() {
            return Result.SUCCESS;
        }
    }
    public record ErrorOutput(Collection<String> messages) implements OutputData {
        @Override
        public Result result() {
            return Result.ERROR;
        }
    }
    
    final QuestionService questionService;
    
    private static void copyVersionFields(Question target, Question source) {
        target.setVersionGroupId(source.getVersionGroupId());
        target.setVersionNumber(source.getVersionNumber());
        target.setVersionStatus(source.getVersionStatus());
        target.setPreviousVersionId(source.getPreviousVersionId());
    }
    
    public CreateOrUpdateMultipleChoicesQuestion(QuestionService questionService,
                                                 VerificationRuleService verificationRuleService,
                                                 QuestionVersionService questionVersionService,
                                                 ScoreRecalculationService scoreRecalculationService) {
        this.questionService = questionService;
        this.verificationRuleService = verificationRuleService;
        this.questionVersionService = questionVersionService;
        this.scoreRecalculationService = scoreRecalculationService;
    }
    
    @Override
    public void execute(ExecuteContext<Input, OutputData> context) {
        final Input input = context.getInput();
        final var multipleChoicesQuestionDTO = input.multipleChoicesQuestionDTO;
        Optional<Question> previousQuestion = questionService.findById(multipleChoicesQuestionDTO.getId());
        final boolean authorChanged = previousQuestion.isPresent() && multipleChoicesQuestionDTO.getAuthorQQ() != null;
        previousQuestion.ifPresent(multipleChoicesQuestionDTO::inheritFrom);
        final ValidationResult result = verificationRuleService.verify(multipleChoicesQuestionDTO, VerificationRuleService.VerifyTargetType.MULTIPLE_CHOICES_QUESTION);
        final Map<String, IssueDTO> errors = result.getErrors();
        if (errors.isEmpty()) {
            if (authorChanged) {
                context.requirePermission("change question author");
            }
            if (previousQuestion.isEmpty() ||
                    previousQuestion.get().getAuthor() != null &&
                            context.isCurrentUser(previousQuestion.get().getAuthor())) {
                context.requirePermission("create and edit owns questions");
            } else {
                context.requirePermission("edit others questions");
            }
            Boolean dtoEnabled = multipleChoicesQuestionDTO.getEnabled();
            if (dtoEnabled != null &&
                    ((previousQuestion.isPresent() && previousQuestion.get().isEnabled() != dtoEnabled) ||
                            (previousQuestion.isEmpty() && dtoEnabled))
            ) {
                context.requirePermission("enable and disable questions");
            }
            
            // Version management: detect changes and handle version/recalculation
            if (previousQuestion.isPresent()) {
                Long currentUserQq = context.getCurrentUser().getQQNumber();
                QuestionVersionService.VersionHandlingResult versionResult = questionVersionService.handleVersionOnUpdate(
                        previousQuestion.get(), multipleChoicesQuestionDTO, currentUserQq);
                
                if (versionResult.newVersionCreated()) {
                    // New version was created and saved by VersionService
                    versionResult.question().setVerificationDigest(verificationRuleService.digest(multipleChoicesQuestionDTO));
                    versionResult.question().setValidationResult(result);
                    questionService.save(versionResult.question());
                    context.resolve(new SuccessOutput(versionResult.question()));
                    return;
                }
                
                if (versionResult.recalculationNeeded()) {
                    // Update in-place then trigger async recalculation
                    Question question = QuestionCreateUtils.createMultipleChoicesQuestion(multipleChoicesQuestionDTO);
                    question.setVerificationDigest(verificationRuleService.digest(multipleChoicesQuestionDTO));
                    question.setValidationResult(result);
                    copyVersionFields(question, previousQuestion.get());
                    questionService.save(question);
                    scoreRecalculationService.triggerAsyncRecalculation(question.getId(), currentUserQq);
                    context.resolve(new SuccessOutput(question));
                    return;
                }
            }
            
            // Default path: no version management needed (new question or no historical answers)
            Question question = QuestionCreateUtils.createMultipleChoicesQuestion(multipleChoicesQuestionDTO);
            question.setVerificationDigest(verificationRuleService.digest(multipleChoicesQuestionDTO));
            question.setValidationResult(result);
            if (previousQuestion.isPresent()) {
                copyVersionFields(question, previousQuestion.get());
            } else if (question.getVersionGroupId() == null) {
                question.setVersionGroupId(question.getId());
            }
            questionService.save(question);
            context.resolve(new SuccessOutput(question));
        } else {
            context.resolve(new CreateOrUpdateMultipleChoicesQuestion.ErrorOutput(errors.values().stream().map(IssueDTO::getContent).toList()));
        }
    }
}
