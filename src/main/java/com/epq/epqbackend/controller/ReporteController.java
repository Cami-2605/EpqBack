package com.epq.epqbackend.controller;

import com.epq.epqbackend.dto.ReporteRequestDto;
import com.epq.epqbackend.service.ReporteService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reportes")
@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
public class ReporteController {
    @Autowired
    private ReporteService reporteService;

    @PostMapping("/generar")
    public ResponseEntity<byte[]> generarReporte(
            @RequestBody ReporteRequestDto requestDto) {

        byte[] archivo = reporteService.generarReporte(
                requestDto.getNombreArchivo(),
                requestDto.getTipoFuente(),
                requestDto.getFechaInicio(),
                requestDto.getFechaFin(),
                requestDto.getMunicipio(),
                requestDto.getTipoReporte(),
                requestDto.getTipoArchivo()
        );

        return ResponseEntity.ok()
                .header("Content-Disposition",
                        "attachment; filename=reporte." + requestDto.getTipoArchivo())
                .header("Content-Type",
                        requestDto.getTipoArchivo().equalsIgnoreCase("pdf")
                                ? "application/pdf"
                                : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(archivo);
    }
}