package indi.etern.checkIn.controller.html;

import indi.etern.checkIn.service.web.TurnstileService;
import indi.etern.checkIn.utils.CookieUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Arrays;
import java.util.regex.Pattern;

@Controller
public class MainController {
    private static final Pattern SAFE_FILENAME = Pattern.compile("[a-zA-Z0-9_-]+");
    private final TurnstileService turnstileService;

    public MainController(TurnstileService turnstileService) {
        this.turnstileService = turnstileService;
    }

    @GetMapping({"/", "/index.html"})
    public String index(HttpServletRequest request, HttpServletResponse response) {
        final Cookie[] cookies = request.getCookies();
        final String referer = request.getHeader("referer");
        final boolean byServiceWorker = referer != null && referer.contains("sw.js"); /*For workbox.js auto ajax request below*/
        final boolean isLogined = cookies != null && Arrays.stream(cookies).anyMatch(cookie -> cookie.getName().equals("token"));
        final boolean isInOAuth2 = cookies != null && Arrays.stream(cookies).anyMatch(cookie -> cookie.getName().equals("OAuth2Mode"));
        boolean enabledOnLogin = turnstileService.isTurnstileEnabledOnLogin();
        boolean enabledOnExam = turnstileService.isTurnstileEnabledOnExam();
        if (enabledOnLogin || enabledOnExam) {
            String siteKey = turnstileService.getSiteKey();
            if (siteKey != null) {
                response.addCookie(CookieUtils.createClientReadableCookie("siteKey", siteKey));
            }
            response.addCookie(CookieUtils.createClientReadableCookie("verifyLogin", String.valueOf(enabledOnLogin)));
            response.addCookie(CookieUtils.createClientReadableCookie("verifyExam", String.valueOf(enabledOnExam)));
        }
        if (byServiceWorker || isInOAuth2) {
            return "front-face/index.html";
        }
        if (isLogined) {
            return "redirect:/manage/";
        } else {
            return "redirect:/exam/";
        }
    }

    @GetMapping("/exam/**")
    public String exam(HttpServletResponse response) {
        boolean enabledOnExam = turnstileService.isTurnstileEnabledOnExam();
        if (enabledOnExam) {
            String siteKey = turnstileService.getSiteKey();
            if (siteKey != null) {
                response.addCookie(CookieUtils.createClientReadableCookie("siteKey", siteKey));
            }
            response.addCookie(CookieUtils.createClientReadableCookie("verifyExam", String.valueOf(enabledOnExam)));
        }
        return "front-face/index.html";
    }

    @GetMapping({"/manage/**", "/login/", "/oauth2/error"})
    public String manage(HttpServletResponse response) {
        boolean enabledOnLogin = turnstileService.isTurnstileEnabledOnLogin();
        if (enabledOnLogin) {
            String siteKey = turnstileService.getSiteKey();
            if (siteKey != null) {
                response.addCookie(CookieUtils.createClientReadableCookie("siteKey", siteKey));
            }
            response.addCookie(CookieUtils.createClientReadableCookie("verifyLogin", String.valueOf(enabledOnLogin)));
        }
        return "front-face/index.html";
    }

    @GetMapping("/manifest.webmanifest")
    public String manifest() {
        return "front-face/manifest.webmanifest";
    }

    @GetMapping("/registerSW.js")
    public String registerSW() {
        return "front-face/registerSW.js";
    }

    @GetMapping("/workbox-{id}.js")
    public String workbox(@PathVariable String id) {
        if (!SAFE_FILENAME.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid workbox file name");
        }
        return "front-face/workbox-" + id + ".js";
    }

    @GetMapping("/sw.js")
    public String sw() {
        return "front-face/sw.js";
    }

    @GetMapping("/icons/{fileName}")
    public String icons(@PathVariable String fileName) {
        if (!SAFE_FILENAME.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Invalid icon file name");
        }
        return "icons/" + fileName;
    }

    @GetMapping("/oauth2/success/**")
    public String oauth2CallbackSuccess() {
        return "front-face/index.html";
    }

    @GetMapping("/oauth2/fail/**")
    public String oauth2CallbackFail() {
        return "front-face/index.html";
    }
}