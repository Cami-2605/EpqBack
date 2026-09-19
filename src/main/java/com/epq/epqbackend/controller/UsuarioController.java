package com.epq.epqbackend.controller;

import com.epq.epqbackend.dto.UsuarioDto;
import com.epq.epqbackend.model.Usuario;
import com.epq.epqbackend.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @GetMapping("/perfil")
    public ResponseEntity<UsuarioDto> obtenerPerfil() {
        // Obtener el username del contexto de seguridad
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        System.out.println("🔍 Buscando perfil de usuario: " + username);

        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado: " + username));

        return ResponseEntity.ok(convertToDto(usuario));
    }

    @PutMapping("/perfil")
    public ResponseEntity<UsuarioDto> actualizarPerfil(@RequestBody UsuarioDto usuarioDto) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        usuario.setNombreCompleto(usuarioDto.getNombreCompleto());
        usuario.setTelefono(usuarioDto.getTelefono());
        usuario.setEmail(usuarioDto.getCorreo());

        if (usuarioDto.getPassword() != null && !usuarioDto.getPassword().isEmpty()) {
            usuario.setPassword(passwordEncoder.encode(usuarioDto.getPassword()));
        }

        usuarioRepository.save(usuario);
        return ResponseEntity.ok(convertToDto(usuario));
    }

    @PostMapping("/perfil/foto")
    public ResponseEntity<String> actualizarFoto(@RequestParam("foto") MultipartFile foto) throws IOException {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        String base64Image = "data:" + foto.getContentType() + ";base64," +
                Base64.getEncoder().encodeToString(foto.getBytes());
        usuario.setFotoPerfil(base64Image);
        usuarioRepository.save(usuario);

        return ResponseEntity.ok(base64Image);
    }

    @GetMapping
    public ResponseEntity<List<UsuarioDto>> listarUsuarios() {
        List<Usuario> usuarios = usuarioRepository.findAll();
        List<UsuarioDto> dtos = usuarios.stream().map(this::convertToDto).collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> eliminarUsuario(@PathVariable Long id) {
        usuarioRepository.deleteById(id);
        return ResponseEntity.ok("Usuario eliminado");
    }

    private UsuarioDto convertToDto(Usuario usuario) {
        UsuarioDto dto = new UsuarioDto();
        dto.setId(usuario.getId());
        dto.setUsername(usuario.getUsername());
        dto.setNombreCompleto(usuario.getNombreCompleto());
        dto.setCorreo(usuario.getEmail());
        dto.setTelefono(usuario.getTelefono());
        dto.setCedula(usuario.getCedula());
        dto.setFotoPerfil(usuario.getFotoPerfil());
        dto.setRol(usuario.getRoles().contains("ROLE_ADMIN") ? "admin" : "user");
        return dto;
    }
}