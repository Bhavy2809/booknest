package com.booknest.security;

import com.booknest.model.User;
import com.booknest.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Runs before every controller method. If the request carries a valid
 * "Authorization: Bearer <token>" header, the matching User is attached to the request.
 * It never blocks the request: public pages simply see "no user",
 * and protected endpoints ask for one through {@link CurrentUser}.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

     static final String ATTRIBUTE = "currentUser";

     private final TokenService tokens;
     private final UserRepository users;

     public AuthInterceptor(TokenService tokens, UserRepository users) {
         this.tokens = tokens;
         this.users = users;
     }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            Long userId = tokens.verify(header.substring(7).trim());
            if (userId != null) {
                users.findById(userId).ifPresent((User u) -> request.setAttribute(ATTRIBUTE, u));
            }
        }
          return true;
     }
}
