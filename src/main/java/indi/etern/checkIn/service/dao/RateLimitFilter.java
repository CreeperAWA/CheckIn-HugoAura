package indi.etern.checkIn.service.dao;

import com.fasterxml.jackson.databind.ObjectMapper;
import indi.etern.checkIn.entities.rateLimit.RateLimitLog;
import indi.etern.checkIn.entities.rateLimit.RateLimitRule;
import indi.etern.checkIn.entities.rateLimit.RateLimitWhitelist;
import indi.etern.checkIn.entities.user.Permission;
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
import java.nio.charset.StandardCharsets;
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
        
        // 包装请求，以便在读取请求体后，后续的控制器仍然能够读取到请求体
        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        
        String ipAddress = getClientIp(cachedRequest);
        String cookieValue = getCookieValue(cachedRequest);
        Long qqNumber = getQqNumber(cachedRequest);
        String oauthInfo = getOAuthInfo(cachedRequest);
        
        // 调试日志：检查权限状态
        Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
        logger.debug("Rate limit check - Auth status: {}, Principal: {}", 
            currentAuth != null ? currentAuth.isAuthenticated() : "null",
            currentAuth != null ? currentAuth.getPrincipal().getClass().getSimpleName() : "null");
        
        if (isSuperAdmin() || hasBypassRateLimitPermission()) {
            logger.info("Rate limit bypassed for user with elevated privileges");
            filterChain.doFilter(cachedRequest, response);
            return;
        }
        
        Map<String, Object> contextInfo = new HashMap<>();
        contextInfo.put("ip", ipAddress);
        contextInfo.put("cookie", cookieValue);
        contextInfo.put("qq", qqNumber);
        contextInfo.put("oauth", oauthInfo);
        
        for (RateLimitRule rule : enabledRules) {
            String identifier = getIdentifierForDimension(rule.getDimension(), cachedRequest, contextInfo);
            
            if (identifier == null || identifier.isEmpty()) continue;
            
            if (isWhitelisted(rule.getDimension(), identifier)) continue;
            
            RateLimitService.RateLimitCheckResult checkResult =
                    rateLimitService.checkRateLimit(identifier, rule);
            
            if (!checkResult.allowed()) {
                handleRateLimited(response, rule, contextInfo, cachedRequest);
                return;
            }
        }
        
        filterChain.doFilter(cachedRequest, response);
    }
    
    private boolean isSuperAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logger.debug("isSuperAdmin check - Auth: {}, isAuthenticated: {}, Principal: {}", 
            auth != null, 
            auth != null && auth.isAuthenticated(),
            auth != null ? auth.getPrincipal().getClass().getName() : "null");
        
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User user) {
            String roleType = user.getRole() != null ? user.getRole().getType() : "null";
            logger.debug("isSuperAdmin - User role: {}", roleType);
            
            if (user.getRole() != null) {
                boolean isSuperAdmin = "super_admin".equals(user.getRole().getType());
                logger.debug("isSuperAdmin result: {}", isSuperAdmin);
                return isSuperAdmin;
            }
            return false;
        }
        return false;
    }
    
    private boolean hasBypassRateLimitPermission() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logger.debug("hasBypassRateLimitPermission check - Auth: {}", auth != null);
        
        if (auth != null && auth.isAuthenticated()) {
            boolean hasPermission = auth.getAuthorities().stream()
                .anyMatch(authority -> Permission.BYPASS_RATE_LIMIT.equals(authority.getAuthority()));
            logger.debug("hasBypassRateLimitPermission result: {}, authorities: {}", 
                hasPermission, 
                auth.getAuthorities().stream().map(Object::toString).toList());
            return hasPermission;
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
                errorResponse.put("retryAfter", rule.getLimitDurationSeconds());
                errorResponse.put("limitDuration", rule.getLimitDurationSeconds());
                response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
            }
            case CUSTOM_MESSAGE -> {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                Map<String, Object> customResponse = new HashMap<>();
                customResponse.put("error", "Rate Limited");
                customResponse.put("message", rule.getCustomMessage() != null ?
                        rule.getCustomMessage() : "请求频率超限，请稍后再试");
                customResponse.put("limitDuration", rule.getLimitDurationSeconds());
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
                delayResponse.put("limitDuration", rule.getLimitDurationSeconds());
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

        // 增强调试信息日志
        if (log.getQqNumber() == null || log.getQqNumber() == 0) {
            logger.warn("Rate limit triggered without valid QQ number - IP: {}, Path: {}, Dimension: {}, " +
                        "Auth: {}",
                log.getIpAddress(),
                log.getRequestPath(),
                log.getTriggeredDimension(),
                (SecurityContextHolder.getContext().getAuthentication() != null &&
                 SecurityContextHolder.getContext().getAuthentication().isAuthenticated() ?
                 "authenticated" : "not authenticated"));
        } else {
            logger.info("Rate limit triggered for QQ: {}, IP: {}, Path: {}, Dimension: {}",
                log.getQqNumber(), log.getIpAddress(), log.getRequestPath(), log.getTriggeredDimension());
        }

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
        Long qqNumber = null;
        String source = "unknown";

        try {
            // 1. 优先从请求属性中获取 QQ 号（如果之前的处理逻辑已经设置了）
            Object qqAttr = request.getAttribute("qqNumber");
            if (qqAttr instanceof Long qq) {
                if (qq != 0 && isValidQQNumber(qq)) {
                    logger.debug("Got QQ number from request attribute: {}", qq);
                    return qq;
                }
            } else {
                logger.debug("qqNumber attribute not found or not Long: {}", qqAttr);
            }

            // 2. 从当前用户对象中获取 QQ 号
            Object userAttr = request.getAttribute("currentUser");
            if (userAttr instanceof User user) {
                qqNumber = user.getQQNumber();
                if (qqNumber != null && qqNumber != 0 && isValidQQNumber(qqNumber)) {
                    source = "currentUser attribute";
                    logger.debug("Got QQ number from currentUser attribute: {}", qqNumber);
                    return qqNumber;
                } else {
                    logger.debug("currentUser QQ invalid: {}", qqNumber);
                }
            } else {
                logger.debug("currentUser attribute not found or not User: {}", userAttr);
            }

            // 3. 从 Spring Security Context 获取当前认证用户（补充途径）
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof User secUser) {
                qqNumber = secUser.getQQNumber();
                if (qqNumber != null && qqNumber != 0 && isValidQQNumber(qqNumber)) {
                    source = "SecurityContext";
                    logger.debug("Got QQ number from SecurityContext: {}", qqNumber);
                    return qqNumber;
                } else {
                    logger.debug("SecurityContext user QQ invalid: {}", qqNumber);
                }
            } else {
                logger.debug("SecurityContext not authenticated or principal not User: auth={}, principal={}", 
                    auth, auth != null ? auth.getPrincipal().getClass().getName() : "null");
            }

            // 4. 从 user cookie 中解析 QQ 号（关键修复：支持正常登录场景）
            qqNumber = getQqFromUserCookie(request);
            if (qqNumber != null && qqNumber != 0 && isValidQQNumber(qqNumber)) {
                source = "user cookie";
                logger.debug("Got QQ number from user cookie: {}", qqNumber);
                return qqNumber;
            }

            // 5. 从独立的 qq cookie 中获取 QQ 号
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie cookie : cookies) {
                    if ("qq".equals(cookie.getName())) {
                        try {
                            qqNumber = Long.parseLong(cookie.getValue());
                            if (qqNumber != 0 && isValidQQNumber(qqNumber)) {
                                source = "qq cookie";
                                logger.debug("Got QQ number from qq cookie: {}", qqNumber);
                                return qqNumber;
                            }
                        } catch (NumberFormatException e) {
                            logger.warn("Invalid QQ number format in qq cookie: {}", cookie.getValue());
                        }
                    }
                }
            }

            // 6. 从请求体中获取 QQ 号（扩展支持更多接口）
            qqNumber = getQqFromRequestBody(request);
            if (qqNumber != null && qqNumber != 0 && isValidQQNumber(qqNumber)) {
                source = "request body";
                logger.debug("Got QQ number from request body: {}", qqNumber);
                return qqNumber;
            }

            // 7. 从 URL 参数中获取 QQ 号（新增）
            qqNumber = getQqFromParameter(request);
            if (qqNumber != null && qqNumber != 0 && isValidQQNumber(qqNumber)) {
                source = "URL parameter";
                logger.debug("Got QQ number from URL parameter: {}", qqNumber);
                return qqNumber;
            }

        } catch (Exception e) {
            logger.error("Error while extracting QQ number from source: {}", source, e);
        }

        logger.warn("Could not extract valid QQ number from any source. Auth status: {}", 
            SecurityContextHolder.getContext().getAuthentication() != null && 
            SecurityContextHolder.getContext().getAuthentication().isAuthenticated() ? 
            "authenticated" : "not authenticated");
        return null;
    }

    /**
     * 验证QQ号是否合法
     * QQ号范围：10000 - 2147483647（约21亿，符合腾讯QQ号规则）
     */
    private boolean isValidQQNumber(Long qqNumber) {
        if (qqNumber == null) return false;
        // QQ号最小为10000，最大不超过Long.MAX_VALUE的合理范围
        return qqNumber >= 10000L && qqNumber <= 99999999999999L;
    }

    /**
     * 从user cookie中解析QQ号
     * user cookie格式：URL编码的JSON字符串，包含qq字段
     */
    private Long getQqFromUserCookie(HttpServletRequest request) {
        try {
            Cookie[] cookies = request.getCookies();
            if (cookies == null) return null;

            for (Cookie cookie : cookies) {
                if ("user".equals(cookie.getName())) {
                    String userCookieValue = cookie.getValue();
                    if (userCookieValue == null || userCookieValue.isEmpty()) continue;

                    try {
                        // 解析URL编码的JSON
                        String decodedValue = java.net.URLDecoder.decode(userCookieValue, StandardCharsets.UTF_8);
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> userData = objectMapper.readValue(decodedValue, java.util.Map.class);

                        if (userData.containsKey("qq")) {
                            Object qqObj = userData.get("qq");
                            if (qqObj instanceof Number number) {
                                return number.longValue();
                            } else if (qqObj instanceof String str) {
                                return Long.parseLong(str);
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("Failed to parse user cookie: {}", e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error extracting QQ from user cookie", e);
        }
        return null;
    }

    /**
     * 从URL参数中获取QQ号
     */
    private Long getQqFromParameter(HttpServletRequest request) {
        try {
            String qqParam = request.getParameter("qq");
            if (qqParam != null && !qqParam.isEmpty()) {
                return Long.parseLong(qqParam);
            }
        } catch (NumberFormatException e) {
            logger.debug("Invalid QQ number format in parameter");
        }
        return null;
    }
    
    private Long getQqFromRequestBody(HttpServletRequest request) {
        try {
            String requestURI = request.getRequestURI();
            String method = request.getMethod();
            logger.debug("Checking request: {} {}", method, requestURI);

            // 仅对 POST/PUT/PATCH 请求解析请求体
            if (!("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method))) {
                return null;
            }

            // 支持的接口列表（可根据需要扩展）
            // 注意：请求路径可能包含上下文路径前缀（如/checkIn/api/generate）
            Set<String> supportedPaths = Set.of(
                "/api/generate",
                "/api/submit",
                "/api/login"
            );

            // 检查是否包含支持的 API 路径（兼容有上下文路径前缀的情况）
            boolean isSupported = supportedPaths.stream().anyMatch(requestURI::endsWith);
            if (isSupported) {
                return extractQQFromJsonBody(request);
            }

            // 对于其他 API 路径，也尝试提取（宽松模式）
            if (requestURI.contains("/api/")) {
                Long qq = extractQQFromJsonBody(request);
                if (qq != null) {
                    logger.debug("Extracted QQ from non-whitelisted API: {}", requestURI);
                    return qq;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to get QQ number from request body", e);
        }
        logger.debug("Returning null for QQ number from request body");
        return null;
    }

    /**
     * 从JSON请求体中提取QQ号
     */
    private Long extractQQFromJsonBody(HttpServletRequest request) {
        try {
            String requestBody;
            try (var reader = request.getReader()) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                requestBody = sb.toString();
            }

            logger.debug("Request body for QQ extraction: {}", requestBody);

            if (requestBody == null || requestBody.isEmpty()) {
                return null;
            }

            // 解析 JSON 请求体
            java.util.Map<String, Object> requestData = objectMapper.readValue(requestBody, java.util.Map.class);

            // 尝试多种可能的字段名
            String[] possibleFields = {"qq", "qqNumber", "userId", "user_qq", "usernameOrQQ"};

            for (String field : possibleFields) {
                if (requestData.containsKey(field)) {
                    Object qqObj = requestData.get(field);
                    logger.debug("Found field '{}' in request body: {}", field, qqObj);

                    if (qqObj instanceof Number number) {
                        long qq = number.longValue();
                        if (isValidQQNumber(qq)) {
                            logger.debug("Successfully parsed QQ number from field '{}': {}", field, qq);
                            return qq;
                        }
                    } else if (qqObj instanceof String str) {
                        try {
                            long qq = Long.parseLong(str);
                            if (isValidQQNumber(qq)) {
                                logger.debug("Successfully parsed QQ number from string field '{}': {}", field, qq);
                                return qq;
                            }
                        } catch (NumberFormatException e) {
                            logger.debug("Field '{}' is not a valid number: {}", field, str);
                        }
                    }
                }
            }

            logger.debug("No valid QQ field found in request body. Available keys: {}", requestData.keySet());
        } catch (Exception e) {
            logger.error("Error parsing request body for QQ extraction", e);
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
    
    // 用于缓存请求体的包装类
    private static class CachedBodyHttpServletRequest extends jakarta.servlet.http.HttpServletRequestWrapper {
        private final byte[] cachedBody;
        
        public CachedBodyHttpServletRequest(jakarta.servlet.http.HttpServletRequest request) throws java.io.IOException {
            super(request);
            // 读取请求体并缓存
            try (jakarta.servlet.ServletInputStream inputStream = request.getInputStream()) {
                this.cachedBody = inputStream.readAllBytes();
            }
        }
        
        @Override
        public java.io.BufferedReader getReader() throws java.io.IOException {
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream()));
        }
        
        @Override
        public jakarta.servlet.ServletInputStream getInputStream() throws java.io.IOException {
            return new jakarta.servlet.ServletInputStream() {
                private final java.io.ByteArrayInputStream inputStream = new java.io.ByteArrayInputStream(cachedBody);
                
                @Override
                public int read() throws java.io.IOException {
                    return inputStream.read();
                }
                
                @Override
                public int read(byte[] b) throws java.io.IOException {
                    return inputStream.read(b);
                }
                
                @Override
                public int read(byte[] b, int off, int len) throws java.io.IOException {
                    return inputStream.read(b, off, len);
                }
                
                @Override
                public long skip(long n) throws java.io.IOException {
                    return inputStream.skip(n);
                }
                
                @Override
                public int available() throws java.io.IOException {
                    return inputStream.available();
                }
                
                @Override
                public void close() throws java.io.IOException {
                    inputStream.close();
                }
                
                @Override
                public boolean isFinished() {
                    try {
                        return inputStream.available() == 0;
                    } catch (Exception e) {
                        return true;
                    }
                }
                
                @Override
                public boolean isReady() {
                    return true;
                }
                
                @Override
                public void setReadListener(jakarta.servlet.ReadListener listener) {
                    // 不支持异步读取
                }
            };
        }
    }
}
