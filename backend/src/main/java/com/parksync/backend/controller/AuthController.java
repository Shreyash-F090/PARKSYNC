package com.parksync.backend.controller;

import com.parksync.backend.dto.ApiDtos.*;
import com.parksync.backend.security.AppPrincipal;
import com.parksync.backend.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
public class AuthController {
    private final AccountService accounts;

    public AuthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegistrationInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(accounts.register(input));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginInput input) {
        return accounts.login(input);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AppPrincipal principal) {
        accounts.logout(principal.id());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal AppPrincipal principal) {
        return accounts.me(principal.id());
    }
}