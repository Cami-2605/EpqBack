package com.epq.epqbackend.controller;

import com.epq.epqbackend.dto.LoginRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequestDto loginDto) {

        if (loginDto.getUsername().equals("admin")
                && loginDto.getPassword().equals("admin123")) {
            return ResponseEntity.ok("LOGIN_ADMIN");
        }

        return ResponseEntity.ok("LOGIN_USUARIO");
    }
}