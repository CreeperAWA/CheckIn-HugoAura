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

/**
 * 第三方API Token实体
 * 
 * 用于存储和管理第三方API的访问令牌信息
 * 每个Token包含以下信息：
 * - id: Token的唯一标识符（UUID）
 * - sid: 第三方API的服务标识符（Service ID）
 * - token: JWT格式的访问令牌
 * - generateTime: Token生成时间
 * - description: Token的描述信息
 * - generateByUserQQ: 创建该Token的用户QQ号
 * 
 * 数据库表：third_party_api_sid_tokens
 */
@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "third_party_api_sid_tokens")
public class ThirdPartyApiTokenItem implements BaseEntity<String> {
    
    /**
     * Token唯一标识符（UUID格式）
     */
    @Id
    @Column(columnDefinition = "char(36)")
    String id;
    
    /**
     * 第三方API服务标识符（Service ID）
     * 用于标识不同的第三方API服务
     */
    @Column(columnDefinition = "varchar(36)")
    String sid;
    
    /**
     * JWT访问令牌
     * 用于第三方API连接时的身份认证
     */
    @Column(columnDefinition = "varchar(512)")
    String token;
    
    /**
     * Token生成时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime generateTime;
    
    /**
     * Token描述信息
     * 用于说明该Token的用途或备注
     */
    @Column(columnDefinition = "varchar(1024)")
    String description;
    
    /**
     * 创建该Token的用户QQ号
     */
    Long generateByUserQQ;
    
    /**
     * 生成新的第三方API Token
     * 
     * @param id Token的唯一标识符（由调用方生成）
     * @param sid 第三方API服务标识符
     * @param description Token描述信息
     * @param applicant 申请创建Token的用户
     * @param jwtTokenProvider JWT令牌提供者
     * @return 新生成的Token项，token字段已自动填充JWT令牌
     */
    public static ThirdPartyApiTokenItem generateNewToken(String id, String sid, String description, User applicant, JwtTokenProvider jwtTokenProvider) {
        final String token = jwtTokenProvider.generateThirdPartyApiSidToken(sid);
        return new ThirdPartyApiTokenItem(id, sid, token, LocalDateTime.now(), description, applicant.getQQNumber());
    }
}
