package com.epq.epqbackend.service;

import com.epq.epqbackend.dto.UsuarioDto;
import com.epq.epqbackend.model.Usuario;
import com.epq.epqbackend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Service
public class UsuarioService {
    @Autowired
    private UsuarioRepository usuarioRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String autenticar(String username, String password) {
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        if (!passwordEncoder.matches(password, usuario.getPassword())) {
            throw new RuntimeException("Contraseña incorrecta");
        }

        return "token-" + System.currentTimeMillis() + "-" + username;
    }

    public void registrarUsuario(UsuarioDto usuarioDto) {
        if (usuarioRepository.existsByUsername(usuarioDto.getUsername())) {
            throw new RuntimeException("El nombre de usuario ya existe");
        }
        if (usuarioRepository.existsByEmail(usuarioDto.getCorreo())) {
            throw new RuntimeException("El correo ya está registrado");
        }

        Usuario usuario = new Usuario();
        usuario.setUsername(usuarioDto.getUsername());
        usuario.setPassword(passwordEncoder.encode(usuarioDto.getPassword()));
        usuario.setNombreCompleto(usuarioDto.getNombreCompleto());
        usuario.setEmail(usuarioDto.getCorreo());
        usuario.setTelefono(usuarioDto.getTelefono());
        usuario.setCedula(usuarioDto.getCedula());
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setActivo(true);
        usuario.setFotoPerfil(usuarioDto.getFotoPerfil());

        Set<String> roles = new HashSet<>();
        roles.add("ROLE_USER");
        usuario.setRoles(roles);

        usuarioRepository.save(usuario);
    }
}