package indi.etern.checkIn.controller.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import indi.etern.checkIn.action.ActionExecutor;
import indi.etern.checkIn.action.interfaces.OutputData;
import indi.etern.checkIn.action.interfaces.ResultContext;
import indi.etern.checkIn.action.role.permission.GetEnabledPermissionsOfRoleAction;
import indi.etern.checkIn.auth.JwtTokenProvider;
import indi.etern.checkIn.entities.setting.SettingItem;
import indi.etern.checkIn.entities.user.User;
import indi.etern.checkIn.service.dao.SettingService;
import indi.etern.checkIn.service.dao.UserService;
import indi.etern.checkIn.service.web.ThirdPartyApiWebSocketService;
import indi.etern.checkIn.service.web.TurnstileService;
import indi.etern.checkIn.throwable.TurnstileException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.SneakyThrows;
import org.apache.coyote.BadRequestException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@RestController
public class LoginController {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+(\\.\\d+)?");
    final JwtTokenProvider jwtTokenProvider;
    final UserService userService;
    final ObjectMapper objectMapper;
    final PasswordEncoder passwordEncoder;
    private final String nameOrQQOrPasswordWrongStr = "{\"result\":\"fail\",\"message\":\"用户名 QQ 或 密码 错误\"}";
    private final String userDisabledStr = "{\"result\":\"fail\",\"message\":\"用户已禁用\"}";
    private final ActionExecutor actionExecutor;
    private final TurnstileService turnstileService;
    private final ThirdPartyApiWebSocketService thirdPartyApiWebSocketService;
    private final SettingService settingService;

    public LoginController(JwtTokenProvider jwtTokenProvider, UserService userService, ObjectMapper objectMapper, PasswordEncoder passwordEncoder, ActionExecutor actionExecutor, TurnstileService turnstileService, ThirdPartyApiWebSocketService thirdPartyApiWebSocketService, SettingService settingService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userService = userService;
        this.objectMapper = objectMapper;
        this.passwordEncoder = passwordEncoder;
        this.actionExecutor = actionExecutor;
        this.turnstileService = turnstileService;
        this.thirdPartyApiWebSocketService = thirdPartyApiWebSocketService;
        this.settingService = settingService;
    }

    public static boolean isNumber(String str) {
        return str != null && NUMBER_PATTERN.matcher(str).matches();
    }

    private String getResponseOf(User user) throws JsonProcessingException {
        if (!user.isEnabled()) {
            return userDisabledStr;
        }
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("result", "success");
        dataMap.put("qq", user.getQQNumber());
        dataMap.put("name", user.getName());
        final String roleType = user.getRole().getType();
        dataMap.put("role", roleType);
        dataMap.put("token", jwtTokenProvider.generateToken(user));
        final ResultContext<OutputData> context = actionExecutor.execute(GetEnabledPermissionsOfRoleAction.class, new GetEnabledPermissionsOfRoleAction.Input(roleType));
        if (context.getOutput() instanceof GetEnabledPermissionsOfRoleAction.SuccessOutput(
                Map<String, List<indi.etern.checkIn.entities.user.Permission>> permissionGroups
        )) {
            dataMap.put("rolePermission", permissionGroups);
        } else {
            throw new IllegalStateException();
        }
        
        // 发送登录成功通知
        try {
            SettingItem loginSuccessEnabled = settingService.getItem("thirdPartyApi.notification", "loginSuccess.enabled");
            if (loginSuccessEnabled.getValue(Boolean.class)) {
                thirdPartyApiWebSocketService.sendLoginSuccessNotification(user.getName(), user.getQQNumber(), roleType);
            }
        } catch (Exception e) {
            // 通知失败不影响登录流程
        }
        
        return objectMapper.writeValueAsString(dataMap);
    }

    private String checkWithUserName(String name, String password) throws JsonProcessingException {
        final List<User> users = userService.findAllByName(name);
        for (User user : users) {
            if (checkPassword(user, password)) {
                return getResponseOf(user);
            }
        }
        // 发送登录失败通知
        sendLoginFailureNotification(name, password);
        return nameOrQQOrPasswordWrongStr;
    }

    @GetMapping(path = "/api/check-turnstile")
    public String checkTurnstile() throws JsonProcessingException {
        boolean enableTurnstileOnLogin = turnstileService.isTurnstileEnabledOnLogin();
        boolean enableTurnstileOnExam = turnstileService.isTurnstileEnabledOnExam();
        Map<String, Object> response = new HashMap<>();
        if (enableTurnstileOnExam || enableTurnstileOnLogin) {
            String siteKey = turnstileService.getSiteKey();
            if (siteKey == null || siteKey.isEmpty()) {
                response.put("enableTurnstileOnLogin", false);
                response.put("enableTurnstileOnExam", false);
                response.put("message", "Turnstile 未配置");
            } else {
                response.put("enableTurnstileOnLogin", enableTurnstileOnLogin);
                response.put("enableTurnstileOnExam", enableTurnstileOnExam);
                response.put("siteKey", siteKey);
            }
        } else {
            response.put("enableTurnstileOnLogin", false);
            response.put("enableTurnstileOnExam", false);
            response.put("message", "Turnstile 未启用");
        }
        return objectMapper.writeValueAsString(response);
    }

    @PostMapping(path = "/api/login")
    public String login(@RequestBody Map<String, Object> body, HttpServletRequest httpServletRequest) throws JsonProcessingException, BadRequestException {
        String usernameOrQQ = (String) body.get("usernameOrQQ");
        String password = (String) body.get("password");

        boolean enableTurnstile = turnstileService.isTurnstileEnabledOnLogin();

        if (enableTurnstile && turnstileService.isServiceEnable()) {
            String turnstileToken = (String) body.get("turnstileToken");
            try {
                turnstileService.check(turnstileToken, httpServletRequest);
            } catch (TurnstileException e) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("result", "fail");
                errorResponse.put("message", e.getMessage());
                return objectMapper.writeValueAsString(errorResponse);
            } catch (BadRequestException e) {
                return "{\"result\":\"fail\",\"message\":\"未成功验证\"}";
            }
        }
        return dispatch(usernameOrQQ, password);
    }

    @SneakyThrows
    private String dispatch(String usernameOrQQ, String password) {
        if (usernameOrQQ == null || password == null) {
            // 发送登录失败通知
            sendLoginFailureNotification(usernameOrQQ, password);
            return nameOrQQOrPasswordWrongStr;
        }
        if (isNumber(usernameOrQQ)) {
            long qq = Long.parseLong(usernameOrQQ);
            final Optional<User> optionalUser = userService.findByQQNumber(qq);
            if (optionalUser.isPresent()) {
                final User user = optionalUser.get();
                if (checkPassword(user, password)) {
                    return getResponseOf(user);
                } else {
                    // 发送登录失败通知
                    sendLoginFailureNotification(usernameOrQQ, password);
                    return nameOrQQOrPasswordWrongStr;
                }
            } else {
                // 用户不存在，不发送通知，直接返回失败
                return nameOrQQOrPasswordWrongStr;
            }
        } else {
            // 不是数字，调用 checkWithUserName 处理
            return checkWithUserName(usernameOrQQ, password);
        }
    }
    
    private void sendLoginFailureNotification(String username, String password) {
        try {
            SettingItem loginFailureEnabled = settingService.getItem("thirdPartyApi.notification", "loginFailure.enabled");
            if (loginFailureEnabled.getValue(Boolean.class)) {
                thirdPartyApiWebSocketService.sendLoginFailureNotification(username, password);
            }
        } catch (Exception e) {
            // 通知失败不影响登录流程
        }
    }

    private boolean checkPassword(User user, String password) {
        return user.getPassword() == null || passwordEncoder.matches(password, user.getPassword());
    }
}