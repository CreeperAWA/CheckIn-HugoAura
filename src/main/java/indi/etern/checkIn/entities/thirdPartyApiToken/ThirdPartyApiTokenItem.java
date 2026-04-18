package indi.etern.checkIn.entities.thirdPartyApiToken;

import com.fasterxml.jackson.annotation.JsonFormat;
import indi.etern.checkIn.auth.JwtTokenProvider;
import indi.etern.checkIn.entities.BaseEntity;
import indi.etern.checkIn.entities.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "third_party_api_sid_tokens")
public class ThirdPartyApiTokenItem implements BaseEntity<String> {
    @Id
    @Column(columnDefinition = "char(36)")
    String id;
    
    @Column(columnDefinition = "varchar(36)")
    String sid;
    
    @Column(columnDefinition = "varchar(512)")
    String token;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime generateTime;
    
    @Column(columnDefinition = "varchar(1024)")
    String description;
    
    Long generateByUserQQ;
    
    public static ThirdPartyApiTokenItem generateNewToken(String id, String sid, String description, User applicant) {
        final String token = JwtTokenProvider.singletonInstance.generateThirdPartyApiSidToken(sid);
        return new ThirdPartyApiTokenItem(id, sid, token, LocalDateTime.now(), description, applicant.getQQNumber());
    }
}
