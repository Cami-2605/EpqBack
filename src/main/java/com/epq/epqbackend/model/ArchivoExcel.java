package com.epq.epqbackend.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
public class ArchivoExcel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombreArchivo;

    private Long peso;

    private LocalDate fechaCarga;

    private String tipo;
}