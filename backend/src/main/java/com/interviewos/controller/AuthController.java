package com.interviewos.controller;

import com.interviewos.dto.AuthDtos.*;
import com.interviewos.security.AuthUser;
import com.interviewos.service.AuthService;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest r) { return auth.register(r); }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String header) {
        try { auth.logout(header.substring(7)); } catch (JwtException | IndexOutOfBoundsException ignored) { /* already invalid */ }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/profile")
    public ProfileView profile(@AuthenticationPrincipal AuthUser u) { return auth.profile(u.id()); }

    @PutMapping("/profile")
    public ProfileView update(@AuthenticationPrincipal AuthUser u, @Valid @RequestBody ProfileUpdate r) { return auth.updateProfile(u.id(), r); }
}
