package com.epq.epqbackend.service;

import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class ReporteService {

    public byte[] generarReporte(
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String municipio,
            String tipoReporte,
            String formato
    ) {
        if (formato.equalsIgnoreCase("PDF")) {
            return generarPdf();
        } else {
            return generarExcel();
        }
    }

    private byte[] generarPdf() {
        return new byte[0];
    }

    private byte[] generarExcel() {
        return new byte[0];
    }
}