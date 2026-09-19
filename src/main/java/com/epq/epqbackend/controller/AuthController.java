package com.epq.epqbackend.controller;

import com.epq.epqbackend.dto.LoginRequestDto;
import com.epq.epqbackend.dto.UsuarioDto;
import com.epq.epqbackend.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class AuthController {
    @Autowired
    private UsuarioService usuarioService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDto loginDto) {
        try {
            String token = usuarioService.autenticar(loginDto.getUsername(), loginDto.getPassword());
            return ResponseEntity.ok(java.util.Map.of("token", token, "username", loginDto.getUsername()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Credenciales inválidas");
        }
    }

    @PostMapping("/registrar")
    public ResponseEntity<?> registrar(@RequestBody UsuarioDto usuarioDto) {
        try {
            usuarioService.registrarUsuario(usuarioDto);
            return ResponseEntity.ok("Usuario registrado correctamente");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/hash")
    public String generarHash(@RequestParam String password) {
        org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder encoder =
                new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder();
        return encoder.encode(password);
    }
}