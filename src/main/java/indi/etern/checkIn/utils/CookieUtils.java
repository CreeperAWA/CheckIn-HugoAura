package indi.etern.checkIn.utils;

import jakarta.servlet.http.Cookie;

public class CookieUtils {
    
    public static Cookie createSecureCookie(String name, String value) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/checkIn");
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }
    
    public static Cookie createSecureCookie(String name, String value, String path) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath(path);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }
    
    public static Cookie createClientReadableCookie(String name, String value) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(false);
        cookie.setSecure(true);
        cookie.setPath("/checkIn");
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }
    
    public static Cookie createClientReadableCookie(String name, String value, String path) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(false);
        cookie.setSecure(true);
        cookie.setPath(path);
        cookie.setAttribute("SameSite", "Lax");
        return cookie;
    }
}
