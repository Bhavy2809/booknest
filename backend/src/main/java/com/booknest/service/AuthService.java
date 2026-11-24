package com.booknest.service;

import com.booknest.dto.Dtos.*;
import com.booknest.model.User;
import com.booknest.repository.UserRepository;
import com.booknest.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {

     private final UserRepository users;
     private final PasswordEncoder encoder;
     private final TokenService tokens;

    public AuthService(UserRepository users, PasswordEncoder encoder, TokenService tokens) {
        this.users = users;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    public AuthResponse register(RegisterRequest r) {
        String email = r.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        User user = new User();
        user.setName(r.name().trim());
        user.setEmail(email);
        user.setPasswordHash(encoder.encode(r.password()));
        user.setRole("USER");           // everyone who signs up is a normal user; admins are created by hand
        users.save(user);
        return new AuthResponse(tokens.create(user.getId()), toDto(user));
    }

    public AuthResponse login(LoginRequest r) {
        User user = users.findByEmailIgnoreCase(r.email().trim())
                .filter(u -> encoder.matches(r.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong email or password"));
        return new AuthResponse(tokens.create(user.getId()), toDto(user));
    }

     public static UserDto toDto(User u) {
        return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getRole());
    }
}
