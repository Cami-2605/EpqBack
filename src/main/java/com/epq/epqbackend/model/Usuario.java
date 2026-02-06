package com.epq.epqbackend.model;

public class Usuario {

    private Long id;
    private String nombreCompleto;
    private String correo;
    private String telefono;
    private String cedula;
    private String username;
    private String password;

    public Usuario(Long id, String nombreCompleto, String correo, String telefono,
                   String cedula, String username, String password) {
        this.id = id;
        this.nombreCompleto = nombreCompleto;
        this.correo = correo;
        this.telefono = telefono;
        this.cedula = cedula;
        this.username = username;
        this.password = password;
    }

    public Long getId() {
        return id;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getCorreo() {
        return correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getCedula() {
        return cedula;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}