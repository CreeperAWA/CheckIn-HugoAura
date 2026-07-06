package indi.etern.checkIn.utils;

import indi.etern.checkIn.dto.manage.question.*;
import indi.etern.checkIn.entities.linkUtils.impl.ToPartitionsLink;
import indi.etern.checkIn.entities.question.impl.Choice;
import indi.etern.checkIn.entities.question.impl.MultipleChoicesQuestion;
import indi.etern.checkIn.entities.question.impl.Partition;
import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.entities.question.impl.QuestionGroup;
import indi.etern.checkIn.service.dao.QuestionService;
import indi.etern.checkIn.service.dao.UserService;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class QuestionCreateUtils {
    private static <T extends CommonQuestionDTO> MultipleChoicesQuestion create(T commonQuestionDTO, LinkHandler<T> linkHandler) {
        String id = commonQuestionDTO.getId();
        Optional<Question> questionOptional = QuestionService.singletonInstance.findById(id);
        
        MultipleChoicesQuestion.Builder builder;
        MultipleChoicesQuestion multipleChoicesQuestion = null;
        if (questionOptional.isEmpty()) {
            builder = new MultipleChoicesQuestion.Builder();
        } else {
            final Question question = questionOptional.get();
            if (question instanceof MultipleChoicesQuestion multipleChoicesQuestion1) {
                multipleChoicesQuestion = multipleChoicesQuestion1;
                builder = MultipleChoicesQuestion.Builder.from(multipleChoicesQuestion1);
            } else {
                throw new RuntimeException("Question is not a multiple choice question");
            }
        }
        builder.setId(id);
        
        final String content = commonQuestionDTO.getContent();
        if (content != null) {
            builder.setQuestionContent(content);
        }

        String explanation = commonQuestionDTO.getExplanation();
        if (explanation != null) {
            builder.setExplanation(explanation);
        }
        
        Boolean enabled = commonQuestionDTO.getEnabled();
        if (enabled != null) {
            builder.setEnable(enabled);
        }
        
        if (commonQuestionDTO instanceof MultipleChoicesQuestionDTO multipleChoicesQuestionDTO) {
            List<ChoiceDTO> choices = multipleChoicesQuestionDTO.getChoices();
            if (choices != null) {
                // Build map of existing choices by ID for reuse
                Map<String, Choice> existingChoiceMap = new HashMap<>();
                if (multipleChoicesQuestion != null && multipleChoicesQuestion.getChoices() != null) {
                    for (Choice existingChoice : multipleChoicesQuestion.getChoices()) {
                        existingChoiceMap.put(existingChoice.id, existingChoice);
                    }
                }
                
                builder.getChoices().clear();
                int orderIndex = 0;
                for (ChoiceDTO choiceDTO : choices) {
                    Choice choice;
                    if (choiceDTO.getId() != null && existingChoiceMap.containsKey(choiceDTO.getId())) {
                        // Reuse existing Choice to avoid ID conflict
                        choice = existingChoiceMap.get(choiceDTO.getId());
                        choice.setContent(choiceDTO.getContent());
                        choice.setIsCorrect(choiceDTO.isCorrect());
                        choice.setOrderIndex(orderIndex);  // Use array index for correct ordering
                    } else {
                        // Create new Choice for new option - always generate new ID
                        choice = new Choice(choiceDTO.getContent(), choiceDTO.isCorrect());
                        choice.setOrderIndex(orderIndex);  // Use array index for correct ordering
                    }
                    builder.addChoice(choice);
                    orderIndex++;
                }
            }
        }
        
        linkHandler.handle(commonQuestionDTO, builder, Optional.ofNullable(multipleChoicesQuestion));
        
        Long authorQQ = commonQuestionDTO.getAuthorQQ();
        if (authorQQ != null) {
            builder.setAuthor(UserService.singletonInstance.findByQQNumber(authorQQ).orElse(null));
        }
        
        List<ImageDTO> imageDTOs = commonQuestionDTO.getImages();
        if (imageDTOs != null) {
            builder.getImageBase64Strings().clear();
            for (ImageDTO imageDTO : imageDTOs) {
                builder.addBase64Image(imageDTO.getName(), imageDTO.getUrl());
            }
        }
        return builder.build();
    }
    
    public static MultipleChoicesQuestion createMultipleChoicesQuestion(MultipleChoicesQuestionDTO questionDTO) {
        return create(questionDTO, (multipleChoicesQuestionDTO, builder, previousQuestion) -> {
            List<String> partitionIds = multipleChoicesQuestionDTO.getPartitionIds();
            if (partitionIds != null) {
                builder.usePartitionLinks(linkWrapper -> {
                    final Set<Partition> targets = linkWrapper.getTargets();
                    targets.clear();
                    for (String partitionId : partitionIds) {
                        targets.add(Partition.ofId(partitionId));
                    }
                    previousQuestion.ifPresent(question -> {
                        final ToPartitionsLink linkWrapper1 = (ToPartitionsLink) question.getLinkWrapper();
                        final Set<Partition> partitions = new HashSet<>(linkWrapper1.getTargets());
                        partitions.removeAll(targets);
                        partitions.forEach(partition -> {
                            partition.getQuestionLinks().remove(linkWrapper1);
                            partition.getEnabledQuestionsSet().remove(question);
                        });
                    });
                });
            }
        });
    }
    
    protected static MultipleChoicesQuestion createSubMultipleChoicesQuestion(MultipleChoicesQuestionDTO multipleChoicesQuestionDTO, QuestionGroup questionGroup) {
        return create(multipleChoicesQuestionDTO, (questionDataMap1, builder1, previousQuestion) -> builder1.useQuestionGroupLinks(linkWrapper -> {
            linkWrapper.setTarget(questionGroup);
        }));
    }
    
    protected static MultipleChoicesQuestion createSubMultipleChoicesQuestionForNewVersion(MultipleChoicesQuestionDTO multipleChoicesQuestionDTO, QuestionGroup questionGroup) {
        MultipleChoicesQuestion.Builder builder = new MultipleChoicesQuestion.Builder();
        // Do NOT set ID - let Builder.build() generate a new one
        
        final String content = multipleChoicesQuestionDTO.getContent();
        if (content != null) {
            builder.setQuestionContent(content);
        }
        
        String explanation = multipleChoicesQuestionDTO.getExplanation();
        if (explanation != null) {
            builder.setExplanation(explanation);
        }
        
        Boolean enabled = multipleChoicesQuestionDTO.getEnabled();
        if (enabled != null) {
            builder.setEnable(enabled);
        }
        
        List<ChoiceDTO> choices = multipleChoicesQuestionDTO.getChoices();
        if (choices != null) {
            builder.getChoices().clear();
            int orderIndex = 0;
            for (ChoiceDTO choiceDTO : choices) {
                // Create new Choice with new ID for new version
                Choice choice = new Choice(choiceDTO.getContent(), choiceDTO.isCorrect());
                choice.setOrderIndex(orderIndex);  // Use array index for correct ordering
                builder.addChoice(choice);
                orderIndex++;
            }
        }
        
        builder.useQuestionGroupLinks(linkWrapper -> {
            linkWrapper.setTarget(questionGroup);
        });
        
        Long authorQQ = multipleChoicesQuestionDTO.getAuthorQQ();
        if (authorQQ != null) {
            builder.setAuthor(UserService.singletonInstance.findByQQNumber(authorQQ).orElse(null));
        }
        
        return builder.build();
    }
    
    public static MultipleChoicesQuestion createMultipleChoicesQuestionForNewVersion(MultipleChoicesQuestionDTO questionDTO) {
        MultipleChoicesQuestion.Builder builder = new MultipleChoicesQuestion.Builder();
        // Do NOT set ID - let Builder.build() generate a new one
        
        final String content = questionDTO.getContent();
        if (content != null) {
            builder.setQuestionContent(content);
        }
        
        String explanation = questionDTO.getExplanation();
        if (explanation != null) {
            builder.setExplanation(explanation);
        }
        
        Boolean enabled = questionDTO.getEnabled();
        if (enabled != null) {
            builder.setEnable(enabled);
        }
        
        List<ChoiceDTO> choices = questionDTO.getChoices();
        if (choices != null) {
            builder.getChoices().clear();
            int orderIndex = 0;
            for (ChoiceDTO choiceDTO : choices) {
                // Create new Choice with new ID for new version
                // Do NOT preserve old Choice ID to avoid unique constraint conflict
                Choice choice = new Choice(choiceDTO.getContent(), choiceDTO.isCorrect());
                choice.setOrderIndex(orderIndex);  // Use array index for correct ordering
                builder.addChoice(choice);
                orderIndex++;
            }
        }
        
        List<String> partitionIds = questionDTO.getPartitionIds();
        if (partitionIds != null) {
            builder.usePartitionLinks(linkWrapper -> {
                final Set<Partition> targets = linkWrapper.getTargets();
                targets.clear();
                for (String partitionId : partitionIds) {
                    targets.add(Partition.ofId(partitionId));
                }
            });
        }
        
        Long authorQQ = questionDTO.getAuthorQQ();
        if (authorQQ != null) {
            builder.setAuthor(UserService.singletonInstance.findByQQNumber(authorQQ).orElse(null));
        }
        
        List<ImageDTO> imageDTOs = questionDTO.getImages();
        if (imageDTOs != null) {
            builder.getImageBase64Strings().clear();
            for (ImageDTO imageDTO : imageDTOs) {
                builder.addBase64Image(imageDTO.getName(), imageDTO.getUrl());
            }
        }
        return builder.build();
    }
    
    public static QuestionGroup createQuestionGroupForNewVersion(QuestionGroupDTO questionGroupDTO) {
        QuestionGroup.Builder builder = new QuestionGroup.Builder();
        // Do NOT set ID - let Builder.build() generate a new one
        
        String content = questionGroupDTO.getContent();
        if (content != null) {
            builder.setContent(content);
        }
        
        String explanation = questionGroupDTO.getExplanation();
        if (explanation != null) {
            builder.setExplanation(explanation);
        }
        
        List<String> partitionIds = questionGroupDTO.getPartitionIds();
        if (partitionIds != null) {
            builder.getPartitions().clear();
            for (String partitionId : partitionIds) {
                builder.addPartition(Partition.ofId(partitionId));
            }
        }
        
        Long authorQQ = questionGroupDTO.getAuthorQQ();
        if (authorQQ != null) {
            builder.setAuthor(UserService.singletonInstance.findByQQNumber(authorQQ).orElse(null));
        }
        
        Boolean enabled = questionGroupDTO.getEnabled();
        if (enabled != null) {
            builder.setEnabled(enabled);
        }
        
        List<ImageDTO> imageDTOS = questionGroupDTO.getImages();
        if (imageDTOS != null) {
            builder.getImageBase64Strings().clear();
            for (ImageDTO imageDTO : imageDTOS) {
                String key = imageDTO.getName();
                String value = imageDTO.getUrl();
                builder.addBase64Image(key, value);
            }
        }
        
        QuestionGroup questionGroup = builder.build();
        
        List<CommonQuestionDTO> questions = questionGroupDTO.getQuestions();
        if (questions != null) {
            for (CommonQuestionDTO questionInfoObj : questions) {
                if (questionInfoObj instanceof MultipleChoicesQuestionDTO multipleChoicesQuestionDTO) {
                    // Use ForNewVersion method to create new Choice IDs for sub-questions
                    MultipleChoicesQuestion multipleChoicesQuestion = createSubMultipleChoicesQuestionForNewVersion(multipleChoicesQuestionDTO, questionGroup);
                    questionGroup.addQuestion(multipleChoicesQuestion);
                }
            }
        }
        return questionGroup;
    }
    
    public static QuestionGroup createQuestionGroup(QuestionGroupDTO questionGroupDTO) {
        String id = questionGroupDTO.getId();
        Optional<Question> questionOptional = QuestionService.singletonInstance.findById(id);
        QuestionGroup.Builder builder;
        if (questionOptional.isPresent() && questionOptional.get() instanceof QuestionGroup previousQuestionGroup) {
            builder = QuestionGroup.Builder.from(previousQuestionGroup);
        } else {
            builder = new QuestionGroup.Builder();
        }
        builder.setId(questionGroupDTO.getId());
        
        String content = questionGroupDTO.getContent();
        if (content != null) {
            builder.setContent(content);
        }

        String explanation = questionGroupDTO.getExplanation();
        if (explanation != null) {
            builder.setExplanation(explanation);
        }
        
        List<String> partitionIds = questionGroupDTO.getPartitionIds();
        if (partitionIds != null) {
            builder.getPartitions().clear();
            for (String partitionId : partitionIds) {
                builder.addPartition(Partition.ofId(partitionId));
            }
        }
        
        Long authorQQ = questionGroupDTO.getAuthorQQ();
        if (authorQQ != null) {
            builder.setAuthor(UserService.singletonInstance.findByQQNumber(authorQQ).orElse(null));
        }
        
        Boolean enabled = questionGroupDTO.getEnabled();
        if (enabled != null) {
            builder.setEnabled(enabled);
        }
        
        List<CommonQuestionDTO> questions = questionGroupDTO.getQuestions();
        if (questions != null) {
            builder.getQuestions().clear();
        }
        List<ImageDTO> imageDTOS = questionGroupDTO.getImages();
        if (imageDTOS != null) {
            builder.getImageBase64Strings().clear();
            for (ImageDTO imageDTO : imageDTOS) {
                String key = imageDTO.getName();
                String value = imageDTO.getUrl();
                builder.addBase64Image(key, value);
            }
        }
        QuestionGroup questionGroup = builder.build();
        if (questions != null) {
            for (CommonQuestionDTO questionInfoObj : questions) {
                if (questionInfoObj instanceof MultipleChoicesQuestionDTO multipleChoicesQuestionDTO) {
                    MultipleChoicesQuestion multipleChoicesQuestion = createSubMultipleChoicesQuestion(multipleChoicesQuestionDTO, questionGroup);
                    questionGroup.addQuestion(multipleChoicesQuestion);
                }
            }
        }
        return questionGroup;
    }
    
    @FunctionalInterface
    private interface LinkHandler<T> {
        @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
        void handle(T commonQuestionDTO, MultipleChoicesQuestion.Builder builder, Optional<MultipleChoicesQuestion> previousQuestion);
    }
}