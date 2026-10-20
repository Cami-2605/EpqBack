package com.epq.epqbackend.controller;

import com.epq.epqbackend.dto.ApiErrorDto;
import com.epq.epqbackend.dto.FiltroReporteDto;
import com.epq.epqbackend.dto.ReporteRequestDto;
import com.epq.epqbackend.exception.EpqException;
import com.epq.epqbackend.service.ReporteGeneradorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller de reportes.
 *
 * Mantiene compatibilidad con la ruta original POST /reportes/generar
 * y además expone rutas nuevas más expresivas:
 *
 *   POST /reportes/generar          — compatibilidad (ReporteRequestDto)
 *   POST /reportes/excel            — genera Excel con FiltroReporteDto
 *   POST /reportes/pdf              — genera PDF  con FiltroReporteDto
 *   POST /reportes/ambos            — genera ZIP  con FiltroReporteDto
 */
@RestController
@RequestMapping("/reportes")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"},
             allowCredentials = "true")
public class ReporteController {

    @Autowired
    private ReporteGeneradorService reporteGeneradorService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ── Compatibilidad con el frontend anterior ────────────────────────────────

    /**
     * Endpoint legado. Convierte ReporteRequestDto → FiltroReporteDto
     * y delega en el nuevo ReporteGeneradorService.
     *
     * Campos que se mapean:
     *   municipio    → municipios (lista de uno)
     *   fechaInicio  → fechaInicio
     *   fechaFin     → fechaFin
     *   tipoReporte  → tiposReporte (lista de uno)
     *   tipoArchivo  → tipoArchivo
     *   nombreArchivo → nombreArchivo
     *   tipoFuente   → tipoFuente
     */
    @PostMapping("/generar")
    public ResponseEntity<?> generarReporteLegado(@RequestBody ReporteRequestDto req) {
        try {
            FiltroReporteDto filtros = convertir(req);
            return ejecutarReporte(filtros);
        } catch (EpqException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorDto(e.getErrorCode().getCodigo(),
                            e.getMessage(), e.getDetalle()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorDto("ERR_404", "Error generando el reporte: " + e.getMessage()));
        }
    }

    // ── Rutas nuevas con FiltroReporteDto completo ─────────────────────────────

    /** Genera reporte en Excel (.xlsx) */
    @PostMapping("/excel")
    public ResponseEntity<?> generarExcel(@RequestBody FiltroReporteDto filtros) {
        try {
            byte[] datos = reporteGeneradorService.generarExcel(filtros);
            return archivoResponse(datos,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "reporte_epq_" + LocalDate.now().format(FMT) + ".xlsx");
        } catch (EpqException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorDto(e.getErrorCode().getCodigo(),
                            e.getMessage(), e.getDetalle()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorDto("ERR_404", e.getMessage()));
        }
    }

    /** Genera reporte en PDF */
    @PostMapping("/pdf")
    public ResponseEntity<?> generarPdf(@RequestBody FiltroReporteDto filtros) {
        try {
            byte[] datos = reporteGeneradorService.generarPdf(filtros);
            return archivoResponse(datos, "application/pdf",
                    "reporte_epq_" + LocalDate.now().format(FMT) + ".pdf");
        } catch (EpqException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorDto(e.getErrorCode().getCodigo(),
                            e.getMessage(), e.getDetalle()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorDto("ERR_404", e.getMessage()));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private ResponseEntity<?> ejecutarReporte(FiltroReporteDto filtros) {
        String fmt = filtros.getTipoArchivo() != null
                ? filtros.getTipoArchivo().toLowerCase() : "xlsx";

        if ("pdf".equals(fmt)) {
            byte[] datos = reporteGeneradorService.generarPdf(filtros);
            return archivoResponse(datos, "application/pdf",
                    "reporte_epq_" + LocalDate.now().format(FMT) + ".pdf");
        } else {
            byte[] datos = reporteGeneradorService.generarExcel(filtros);
            return archivoResponse(datos,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "reporte_epq_" + LocalDate.now().format(FMT) + ".xlsx");
        }
    }

    private ResponseEntity<?> archivoResponse(byte[] datos,
                                               String contentType,
                                               String nombreArchivo) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + nombreArchivo + "\"");
        headers.set(HttpHeaders.CONTENT_TYPE, contentType);
        headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS,
                HttpHeaders.CONTENT_DISPOSITION);
        return ResponseEntity.ok().headers(headers).body(datos);
    }

    /**
     * Convierte el DTO legado al nuevo FiltroReporteDto.
     * Intenta inferir el tipo de reporte del campo tipoReporte si está presente.
     */
    private FiltroReporteDto convertir(ReporteRequestDto req) {
        FiltroReporteDto f = new FiltroReporteDto();

        // Municipio único → lista
        if (req.getMunicipio() != null && !req.getMunicipio().isBlank()) {
            f.setMunicipios(List.of(req.getMunicipio().trim().toUpperCase()));
        }

        f.setFechaInicio(req.getFechaInicio());
        f.setFechaFin(req.getFechaFin());
        f.setNombreArchivo(req.getNombreArchivo());
        f.setTipoFuente(req.getTipoFuente());
        f.setTipoArchivo(req.getTipoArchivo() != null ? req.getTipoArchivo() : "xlsx");

        // Mapear tipoReporte al nuevo sistema
        if (req.getTipoReporte() != null && !req.getTipoReporte().isBlank()) {
            String tipo = req.getTipoReporte().trim().toUpperCase()
                    .replace(" ", "_").replace("-", "_");
            // intentar reconocer alias comunes
            String tipoNorm = switch (tipo) {
                case "MOROSOS", "DEUDORES", "CARTERA",
                     "DEUDORES_MOROSOS"              -> "DEUDORES_MOROSOS";
                case "MUNICIPIO", "TOTALES_MUNICIPIO",
                     "POR_MUNICIPIO"                 -> "TOTALES_MUNICIPIO";
                case "ESTRATO", "TOTALES_ESTRATO",
                     "POR_ESTRATO"                   -> "TOTALES_ESTRATO";
                case "MES", "AGRUPADO_MES",
                     "POR_MES", "PERIODO"            -> "AGRUPADO_MES";
                case "VIVIENDA", "DETALLE",
                     "DETALLE_VIVIENDA"              -> "DETALLE_VIVIENDA";
                default                              -> "COMBINADO";
            };
            f.setTiposReporte(List.of(tipoNorm));
        }

        // Activar agrupaciones según tipo
        if (f.getTiposReporte() != null) {
            String t = f.getTiposReporte().get(0);
            f.setAgruparPorMunicipio("TOTALES_MUNICIPIO".equals(t) || "COMBINADO".equals(t));
            f.setAgruparPorMes("AGRUPADO_MES".equals(t) || "COMBINADO".equals(t));
            f.setAgruparPorEstrato("TOTALES_ESTRATO".equals(t) || "COMBINADO".equals(t));
            f.setSoloMorosos("DEUDORES_MOROSOS".equals(t));
        }

        return f;
    }
}
