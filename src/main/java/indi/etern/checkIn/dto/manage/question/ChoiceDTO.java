package indi.etern.checkIn.dto.manage.question;

import indi.etern.checkIn.entities.question.impl.Choice;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChoiceDTO {
    private String id;
    private String content;
    private boolean correct;
    private int orderIndex;
    
    public ChoiceDTO(Choice choice) {
        this.id = choice.getId();
        this.content = choice.getContent();
        this.correct = choice.getIsCorrect();
        this.orderIndex = choice.getOrderIndex();
    }
    
    public Choice toChoice() {
        // Always create new Choice with new ID
        // ID preservation is handled explicitly in QuestionCreateUtils.create()
        final Choice choice = new Choice(content, correct);
        choice.setOrderIndex(orderIndex);
        return choice;
    }
}
