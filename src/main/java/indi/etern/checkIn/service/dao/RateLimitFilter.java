package indi.etern.checkIn.service.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import indi.etern.checkIn.entities.rateLimit.RateLimitLog;
import indi.etern.checkIn.entities.rateLimit.RateLimitRule;
import indi.etern.checkIn.entities.rateLimit.RateLimitWhitelist;
import indi.etern.checkIn.entities.user.User;
import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class RateLimitFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(RateLimitFilter.class);
    private final RateLimitService rateLimitService;
    private final RateLimitLogService logService;
    private final ObjectMapper objectMapper;
    
    @Autowired
    public RateLimitFilter(RateLimitService rateLimitService, RateLimitLogService logService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.logService = logService;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                        FilterChain filterChain) throws IOException, ServletException {
        if (!(servletRequest instanceof HttpServletRequest request) ||
            !(servletResponse instanceof HttpServletResponse response)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }
        
        List<RateLimitRule> enabledRules = rateLimitService.getEnabledRules();
        if (enabledRules.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String ipAddress = getClientIp(request);
        String cookieValue = getCookieValue(request);
        Long qqNumber = getQqNumber(request);
        String oauthInfo = getOAuthInfo(request);
        
        if (isSuperAdmin() || hasBypassRateLimitPermission()) {
            filterChain.doFilter(request, response);
            return;
        }
        
        Map<String, Object> contextInfo = new HashMap<>();
        contextInfo.put("ip", ipAddress);
        contextInfo.put("cookie", cookieValue);
        contextInfo.put("qq", qqNumber);
        contextInfo.put("oauth", oauthInfo);
        
        for (RateLimitRule rule : enabledRules) {
            String identifier = getIdentifierForDimension(rule.getDimension(), request, contextInfo);
            
            if (identifier == null || identifier.isEmpty()) continue;
            
            if (isWhitelisted(rule.getDimension(), identifier)) continue;
            
            RateLimitService.RateLimitCheckResult checkResult =
                    rateLimitService.checkRateLimit(identifier, rule);
            
            if (!checkResult.allowed()) {
                handleRateLimited(response, rule, contextInfo, request);
                return;
            }
        }
        
        filterChain.doFilter(request, response);
    }
    
    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            if (user.getRole() != null) {
                return "super_admin".equals(user.getRole().getType());
            }
            return false;
        }
        return false;
    }
    
    private boolean hasBypassRateLimitPermission() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getAuthorities().stream()
                .anyMatch(authority -> "BYPASS_RATE_LIMIT".equals(authority.getAuthority()));
        }
        return false;
    }
    
    private boolean isWhitelisted(RateLimitRule.RateLimitDimension dimension, String value) {
        try {
            RateLimitWhitelist.WhitelistDimension whitelistDim =
                RateLimitWhitelist.WhitelistDimension.valueOf(dimension.name());
            return rateLimitService.isWhitelisted(whitelistDim, value);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    private String getIdentifierForDimension(RateLimitRule.RateLimitDimension dimension,
                                            HttpServletRequest request,
                                            Map<String, Object> context) {
        return switch (dimension) {
            case IP -> (String) context.get("ip");
            case COOKIE -> (String) context.get("cookie");
            case QQ -> {
                Long qq = (Long) context.get("qq");
                yield qq != null ? String.valueOf(qq) : null;
            }
            case OAUTH -> (String) context.get("oauth");
        };
    }
    
    private void handleRateLimited(HttpServletResponse response, RateLimitRule rule,
                                  Map<String, Object> context, HttpServletRequest request)
                                  throws IOException {
        RateLimitLog logEntry = createLogEntry(rule, context, request);
        logService.logRateLimitAsync(logEntry);
        
        switch (rule.getResponseStrategy()) {
            case RETURN_429 -> {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("error", "Too Many Requests");
                errorResponse.put("message", "请求过于频繁，请稍后再试");
                errorResponse.put("retryAfter", rule.getTimeWindowSeconds());
                response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            }
            case CUSTOM_MESSAGE -> {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> customResponse = new HashMap<>();
                customResponse.put("error", "Rate Limited");
                customResponse.put("message", rule.getCustomMessage() != null ?
                        rule.getCustomMessage() : "请求频率超限，请稍后再试");
                response.getWriter().write(objectMapper.writeValueAsString(customResponse));
            }
            case PROGRESSIVE_DELAY -> {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                int currentCount = getCurrentCountForRule(rule, context);
                int delayMs = calculateProgressiveDelay(currentCount, rule.getBaseDelayMs(), rule.getMaxRequests());
                Map<String, Object> delayResponse = new HashMap<>();
                delayResponse.put("error", "Rate Limited");
                delayResponse.put("message", "请求频率超限，已应用渐进式延迟");
                delayResponse.put("appliedDelayMs", delayMs);
                delayResponse.put("currentCount", currentCount);
                delayResponse.put("maxRequests", rule.getMaxRequests());
                response.getWriter().write(objectMapper.writeValueAsString(delayResponse));
            }
        }
    }
    
    private int getCurrentCountForRule(RateLimitRule rule, Map<String, Object> context) {
        String identifier = getIdentifierForDimension(rule.getDimension(), null, context);
        if (identifier == null) return rule.getMaxRequests() + 1;
        return rateLimitService.getCurrentCount(identifier, rule);
    }
    
    private int calculateProgressiveDelay(int currentCount, int baseDelayMs, int maxRequests) {
        int exceedAmount = Math.max(0, currentCount - maxRequests);
        return baseDelayMs + (exceedAmount * baseDelayMs / 10);
    }
    
    private RateLimitLog createLogEntry(RateLimitRule rule, Map<String, Object> context,
                                       HttpServletRequest request) {
        RateLimitLog log = new RateLimitLog();
        log.setId(java.util.UUID.randomUUID().toString());
        log.setIpAddress((String) context.get("ip"));
        log.setCookieValue((String) context.get("cookie"));
        log.setQqNumber((Long) context.get("qq"));
        log.setOauthInfo((String) context.get("oauth"));
        log.setRequestPath(request.getRequestURI());
        log.setRequestMethod(request.getMethod());
        log.setTriggeredRuleId(rule.getId());
        log.setTriggeredDimension(rule.getDimension());
        log.setResponseAction(rule.getResponseStrategy().name());
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    private String getCookieValue(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if ("JSESSIONID".equals(cookie.getName()) || 
                "token".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
    
    private Long getQqNumber(HttpServletRequest request) {
        Object userAttr = request.getAttribute("currentUser");
        if (userAttr instanceof User user) {
            return user.getQQNumber();
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("qq".equals(cookie.getName())) {
                    try {
                        return Long.parseLong(cookie.getValue());
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }
    
    private String getOAuthInfo(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getName() != null) {
            return auth.getName();
        }
        return null;
    }
}
