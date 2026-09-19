package com.epq.epqbackend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "archivos_excel")
public class ArchivoExcel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String nombreArchivo;

    private Long peso;
    private LocalDateTime fechaCarga;
    private String tipo;
    private Integer registrosProcesados = 0;
    private Integer registrosFallidos = 0;
    private String errores;
    private String estado;

    public ArchivoExcel() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public Long getPeso() {
        return peso;
    }

    public void setPeso(Long peso) {
        this.peso = peso;
    }

    public LocalDateTime getFechaCarga() {
        return fechaCarga;
    }

    public void setFechaCarga(LocalDateTime fechaCarga) {
        this.fechaCarga = fechaCarga;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Integer getRegistrosProcesados() {
        return registrosProcesados;
    }

    public void setRegistrosProcesados(Integer registrosProcesados) {
        this.registrosProcesados = registrosProcesados;
    }

    public Integer getRegistrosFallidos() {
        return registrosFallidos;
    }

    public void setRegistrosFallidos(Integer registrosFallidos) {
        this.registrosFallidos = registrosFallidos;
    }

    public String getErrores() {
        return errores;
    }

    public void setErrores(String errores) {
        this.errores = errores;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}