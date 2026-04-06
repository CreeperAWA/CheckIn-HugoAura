package indi.etern.checkIn;

import indi.etern.checkIn.service.dao.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class RateLimitConfig {
    
    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter rateLimitFilter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(rateLimitFilter);
        registration.addUrlPatterns("/api/*");
        // 确保在 JWT 认证过滤器之后执行，这样才能获取到已认证的用户信息
        // Spring Security 的过滤器链 order 为 0
        // JwtAuthenticationFilter 通过 addFilterBefore 添加到 Security 过滤器链中
        // 我们需要确保限流过滤器在它们之后执行，设置为 LOWEST_PRECEDENCE 确保最后执行
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 100);
        registration.setName("rateLimitFilter");
        return registration;
    }
}
