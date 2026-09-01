package com.studymatching.auth.controller;

import com.studymatching.auth.dto.RegisterRequest;
import com.studymatching.auth.dto.RegisterResponse;
import com.studymatching.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.studymatching.auth.dto.LoginRequest;
import com.studymatching.auth.dto.LoginResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResponse register(
            @Valid @RequestBody RegisterRequest request
    ) {
        return authService.register(request);
    }
}