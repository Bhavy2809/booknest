package com.booknest.controller;

import com.booknest.dto.Dtos.*;
import com.booknest.security.CurrentUser;
import com.booknest.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

     private final AuthService auth;
     private final CurrentUser currentUser;

     public AuthController(AuthService auth, CurrentUser currentUser) {
         this.auth = auth;
         this.currentUser = currentUser;
     }

     @PostMapping("/register")
     @ResponseStatus(HttpStatus.CREATED)
     public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
          return auth.register(request);
     }

     @PostMapping("/login")
     public AuthResponse login(@Valid @RequestBody LoginRequest request) {
         return auth.login(request);
     }

    /** Lets the React app check that a stored token is still valid after a page refresh. */
    @GetMapping("/me")
    public UserDto me() {
        return AuthService.toDto(currentUser.require());
    }
}
