package com.epq.epqbackend.model;

import java.time.LocalDate;

public class Reporte {

    private Long id;
    private String tipoReporte;
    private String tipoArchivo;

    private Municipio municipio;

    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    private LocalDate fechaGeneracion;
}