package com.booknest.security;

import com.booknest.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** Helper that controllers use to ask "who is calling?". */
@Component
public class CurrentUser {

    /** The logged-in user, or null for visitors. */
    public User orNull() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        return (User) attrs.getAttribute(AuthInterceptor.ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    }

    /** The logged-in user, or HTTP 401 Unauthorized. */
    public User require() {
        User user = orNull();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in first");
        return user;
    }

    /** The logged-in admin, or 401 / HTTP 403 Forbidden. */
    public User requireAdmin() {
        User user = require();
        if (!user.isAdmin()) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only admins can do this");
        return user;
    }
}
