package indi.etern.checkIn.service.question;

import indi.etern.checkIn.dto.manage.question.ChoiceDTO;
import indi.etern.checkIn.dto.manage.question.CommonQuestionDTO;
import indi.etern.checkIn.dto.manage.question.MultipleChoicesQuestionDTO;
import indi.etern.checkIn.dto.manage.question.QuestionGroupDTO;
import indi.etern.checkIn.entities.question.impl.Choice;
import indi.etern.checkIn.entities.question.impl.MultipleChoicesQuestion;
import indi.etern.checkIn.entities.question.impl.Question;
import indi.etern.checkIn.entities.question.impl.QuestionGroup;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuestionChangeDetector {
    
    public enum ChangeType {
        NO_CHANGE,
        ANSWER_KEY_CHANGE,
        CONTENT_CHANGE,
        MIXED_CHANGE
    }
    
    public ChangeType detectChange(Question oldQuestion, CommonQuestionDTO newQuestionDTO) {
        if (oldQuestion instanceof MultipleChoicesQuestion oldMCQ
                && newQuestionDTO instanceof MultipleChoicesQuestionDTO newMCQDTO) {
            return detectMultipleChoicesChange(oldMCQ, newMCQDTO);
        } else if (oldQuestion instanceof QuestionGroup oldQG
                && newQuestionDTO instanceof QuestionGroupDTO newQGDTO) {
            return detectQuestionGroupChange(oldQG, newQGDTO);
        }
        return ChangeType.CONTENT_CHANGE;
    }
    
    private ChangeType detectMultipleChoicesChange(MultipleChoicesQuestion old, MultipleChoicesQuestionDTO newDTO) {
        boolean contentChanged = false;
        boolean answerKeyChanged = false;
        
        if (newDTO.getContent() != null && !newDTO.getContent().equals(old.getContent())) {
            contentChanged = true;
        }
        
        if (newDTO.getExplanation() != null && !newDTO.getExplanation().equals(old.getExplanation())) {
            contentChanged = true;
        }
        
        List<ChoiceDTO> newChoices = newDTO.getChoices();
        if (newChoices != null) {
            List<Choice> oldChoices = old.getChoices();
            
            // Order-independent comparison by choice content (robust: no dependency on IDs or order).
            // Reordering options only changes order, not the set of contents or the content->correct
            // mapping, so it is correctly detected as NO_CHANGE.
            //
            // Group correctness flags by content instead of collapsing into a single-value map, so
            // that options sharing identical content (duplicates) do not silently lose their
            // correctness information.
            Map<String, List<Boolean>> oldContentToCorrect = oldChoices.stream()
                    .collect(Collectors.groupingBy(Choice::getContent,
                            Collectors.mapping(Choice::isCorrect, Collectors.toList())));
            Map<String, List<Boolean>> newContentToCorrect = newChoices.stream()
                    .collect(Collectors.groupingBy(ChoiceDTO::getContent,
                            Collectors.mapping(ChoiceDTO::isCorrect, Collectors.toList())));
            
            Set<String> allContents = new HashSet<>(oldContentToCorrect.keySet());
            allContents.addAll(newContentToCorrect.keySet());
            
            for (String content : allContents) {
                List<Boolean> oldCorrects = oldContentToCorrect.get(content);
                List<Boolean> newCorrects = newContentToCorrect.get(content);
                
                // Content change: option added/removed, or its multiplicity (duplicate count) changed
                if (oldCorrects == null || newCorrects == null || oldCorrects.size() != newCorrects.size()) {
                    contentChanged = true;
                    continue;
                }
                
                // Answer key change: same content and multiplicity, but correctness distribution flipped
                List<Boolean> sortedOld = oldCorrects.stream().sorted().toList();
                List<Boolean> sortedNew = newCorrects.stream().sorted().toList();
                if (!sortedOld.equals(sortedNew)) {
                    answerKeyChanged = true;
                }
            }
        }
        
        if (contentChanged && answerKeyChanged) return ChangeType.MIXED_CHANGE;
        if (answerKeyChanged) return ChangeType.ANSWER_KEY_CHANGE;
        if (contentChanged) return ChangeType.CONTENT_CHANGE;
        return ChangeType.NO_CHANGE;
    }
    
    private ChangeType detectQuestionGroupChange(QuestionGroup old, QuestionGroupDTO newDTO) {
        boolean contentChanged = false;
        boolean answerKeyChanged = false;
        
        if (newDTO.getContent() != null && !newDTO.getContent().equals(old.getContent())) {
            contentChanged = true;
        }
        
        if (newDTO.getExplanation() != null && !newDTO.getExplanation().equals(old.getExplanation())) {
            contentChanged = true;
        }
        
        List<CommonQuestionDTO> newQuestions = newDTO.getQuestions();
        if (newQuestions != null) {
            List<Question> oldSubQuestions = old.getQuestionLinks().stream()
                    .sorted((a, b) -> Integer.compare(a.getOrderIndex(), b.getOrderIndex()))
                    .map(link -> link.getSource())
                    .toList();
            
            if (newQuestions.size() != oldSubQuestions.size()) {
                contentChanged = true;
            } else {
                for (int i = 0; i < newQuestions.size(); i++) {
                    CommonQuestionDTO subDTO = newQuestions.get(i);
                    Question oldSub = oldSubQuestions.get(i);
                    
                    if (subDTO instanceof MultipleChoicesQuestionDTO subMCQDTO
                            && oldSub instanceof MultipleChoicesQuestion oldSubMCQ) {
                        ChangeType subChange = detectMultipleChoicesChange(oldSubMCQ, subMCQDTO);
                        if (subChange == ChangeType.CONTENT_CHANGE) contentChanged = true;
                        if (subChange == ChangeType.ANSWER_KEY_CHANGE) answerKeyChanged = true;
                        if (subChange == ChangeType.MIXED_CHANGE) {
                            contentChanged = true;
                            answerKeyChanged = true;
                        }
                    }
                }
            }
        }
        
        if (contentChanged && answerKeyChanged) return ChangeType.MIXED_CHANGE;
        if (answerKeyChanged) return ChangeType.ANSWER_KEY_CHANGE;
        if (contentChanged) return ChangeType.CONTENT_CHANGE;
        return ChangeType.NO_CHANGE;
    }
}
