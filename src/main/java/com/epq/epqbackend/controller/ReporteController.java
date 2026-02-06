package com.epq.epqbackend.controller;

import com.epq.epqbackend.dto.ReporteDto;
import com.epq.epqbackend.dto.ReporteRequestDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reportes")
public class ReporteController {

    @PostMapping("/generar")
    public ResponseEntity<ReporteDto> generarReporte(
            @RequestBody ReporteRequestDto requestDto) {

        ReporteDto reporte = new ReporteDto();
        reporte.setTipoReporte(requestDto.getTipoReporte());
        reporte.setTipoArchivo(requestDto.getTipoArchivo());

        return ResponseEntity.ok(reporte);
    }

    @GetMapping("/descargar/pdf/{id}")
    public ResponseEntity<byte[]> descargarPdf(@PathVariable Long id) {
        return ResponseEntity.ok(new byte[0]);
    }

    @GetMapping("/descargar/excel/{id}")
    public ResponseEntity<byte[]> descargarExcel(@PathVariable Long id) {
        return ResponseEntity.ok(new byte[0]);
    }
}