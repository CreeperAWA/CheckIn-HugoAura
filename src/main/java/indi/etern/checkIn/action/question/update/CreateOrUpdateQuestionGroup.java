package indi.etern.checkIn.action.question.update;

import indi.etern.checkIn.action.BaseAction;
import indi.etern.checkIn.action.interfaces.Action;
import indi.etern.checkIn.action.interfaces.ExecuteContext;
import indi.etern.checkIn.action.interfaces.InputData;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.dto.manage.question.CommonQuestionDTO;
import indi.etern.checkIn.dto.manage.question.IssueDTO;
import indi.etern.checkIn.dto.manage.question.QuestionGroupDTO;
import indi.etern.checkIn.entities.linkUtils.impl.QuestionLinkImpl;
import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.entities.question.impl.QuestionGroup;
import indi.etern.checkIn.service.dao.QuestionService;
import indi.etern.checkIn.service.dao.VerificationRuleService;
import indi.etern.checkIn.service.dao.verify.ValidationResult;
import indi.etern.checkIn.service.question.QuestionVersionService;
import indi.etern.checkIn.service.question.ScoreRecalculationService;
import indi.etern.checkIn.service.exam.StatusService;
import indi.etern.checkIn.utils.QuestionCreateUtils;
import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Action(value = "createOrUpdateQuestionGroup",exposed = false)
public class CreateOrUpdateQuestionGroup extends BaseAction<CreateOrUpdateQuestionGroup.Input, OutputData> {
    private final VerificationRuleService verificationRuleService;
    private final QuestionVersionService questionVersionService;
    private final ScoreRecalculationService scoreRecalculationService;
    
    public record Input(@Nonnull QuestionGroupDTO questionGroupDTO) implements InputData {}
    public record SuccessOutput(QuestionGroup questionGroup) implements OutputData {
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

    public CreateOrUpdateQuestionGroup(QuestionService questionService,
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
        final var questionGroupDTO = input.questionGroupDTO;
        Optional<Question> previousQuestion = questionService.findById(questionGroupDTO.getId());
        if (previousQuestion.isPresent() && previousQuestion.get().getVersionStatus() == Question.VersionStatus.ARCHIVED) {
            context.resolve(new ErrorOutput(List.of("无法修改已归档题目")));
            return;
        }
        final boolean authorChanged = previousQuestion.isPresent() && questionGroupDTO.getAuthorQQ() != null;
        previousQuestion.ifPresent(questionGroupDTO::inheritFrom);
        final ValidationResult result = verificationRuleService.verify(questionGroupDTO, VerificationRuleService.VerifyTargetType.QUESTION_GROUP);
        final Map<String, IssueDTO> errors = result.getErrors();
        if (errors.isEmpty()) {
            if (authorChanged) {
                context.requirePermission("change question group author");
            }
            if (previousQuestion.isEmpty() ||
                    previousQuestion.get().getAuthor() != null &&
                            context.isCurrentUser(previousQuestion.get().getAuthor())) {
                context.requirePermission("create and edit owns question groups");
            } else {
                context.requirePermission("edit others question groups");
            }
            Boolean dtoEnabled = questionGroupDTO.getEnabled();
            if (dtoEnabled != null &&
                    ((previousQuestion.isPresent() && previousQuestion.get().isEnabled() != dtoEnabled) ||
                            (previousQuestion.isEmpty() && dtoEnabled))
            ) {
                context.requirePermission("enable and disable question groups");
            }
            int count = 0;
            for (CommonQuestionDTO commonQuestionDTO : questionGroupDTO.getQuestions()) {
                final ValidationResult result1 = verificationRuleService.verify(commonQuestionDTO, VerificationRuleService.VerifyTargetType.MULTIPLE_CHOICES_QUESTION);
                if (!result1.getErrors().isEmpty()) {
                    count++;
                    for (Map.Entry<String, IssueDTO> entry : result1.getErrors().entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue().getContent();
                        errors.put(count + "-" + key, new IssueDTO("第" + (count) + "道子题目：" +value));
                    }
                }
            }
            
            if (errors.isEmpty()) {
                // Version management: detect changes and handle version/recalculation
                Long currentUserQq = context.getCurrentUser().getQQNumber();
                if (previousQuestion.isPresent()) {
                    QuestionVersionService.VersionHandlingResult versionResult = questionVersionService.handleVersionOnUpdate(
                            previousQuestion.get(), questionGroupDTO, currentUserQq);
                    
                    if (versionResult.newVersionCreated()) {
                        if (versionResult.question() instanceof QuestionGroup newVersionGroup) {
                            newVersionGroup.setVerificationDigest(verificationRuleService.digest(questionGroupDTO));
                            newVersionGroup.setValidationResult(result);
                            questionService.saveAll(newVersionGroup.getQuestionLinks().stream().map(QuestionLinkImpl::getSource).toList());
                            questionService.save(newVersionGroup);
                            if (versionResult.recalculationNeeded()) {
                                scoreRecalculationService.triggerAsyncRecalculation(newVersionGroup.getId(), currentUserQq);
                            }
                            context.resolve(new SuccessOutput(newVersionGroup));
                            StatusService.singletonInstance.flush();
                            return;
                        }
                    }
                    
                    if (versionResult.recalculationNeeded()) {
                        final QuestionGroup questionGroup = QuestionCreateUtils.createQuestionGroup(questionGroupDTO);
                        questionGroup.setVerificationDigest(verificationRuleService.digest(questionGroupDTO));
                        questionGroup.setValidationResult(result);
                        copyVersionFields(questionGroup, previousQuestion.get());
                        questionService.saveAll(questionGroup.getQuestionLinks().stream().map(QuestionLinkImpl::getSource).toList());
                        questionService.save(questionGroup);
                        scoreRecalculationService.triggerAsyncRecalculation(questionGroup.getId(), currentUserQq);
                        context.resolve(new SuccessOutput(questionGroup));
                        StatusService.singletonInstance.flush();
                        return;
                    }
                }
                
                // Default path
                final QuestionGroup questionGroup = QuestionCreateUtils.createQuestionGroup(questionGroupDTO);
                questionGroup.setVerificationDigest(verificationRuleService.digest(questionGroupDTO));
                questionGroup.setValidationResult(result);
                if (previousQuestion.isPresent()) {
                    copyVersionFields(questionGroup, previousQuestion.get());
                } else {
                    questionGroup.setVersionGroupId(questionGroup.getId());
                    questionVersionService.initializeNewQuestionVersion(questionGroup, currentUserQq);
                }
                questionService.saveAll(questionGroup.getQuestionLinks().stream().map(QuestionLinkImpl::getSource).toList());
                questionService.save(questionGroup);
                context.resolve(new SuccessOutput(questionGroup));
                StatusService.singletonInstance.flush();
            } else {
                context.resolve(new ErrorOutput(errors.values().stream().map(IssueDTO::getContent).toList()));
            }
        } else {
            context.resolve(new ErrorOutput(errors.values().stream().map(IssueDTO::getContent).toList()));
        }
    }
}
