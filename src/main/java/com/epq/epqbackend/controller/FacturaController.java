package com.epq.epqbackend.controller;

import com.epq.epqbackend.dto.ApiErrorDto;
import com.epq.epqbackend.dto.FiltroReporteDto;
import com.epq.epqbackend.exception.EpqException;
import com.epq.epqbackend.service.ArchivoProcessorService;
import com.epq.epqbackend.service.FacturaService;
import com.epq.epqbackend.service.ReporteGeneradorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Controller de facturas y reportes.
 *
 * Endpoints:
 *
 * CARGA DE ARCHIVOS
 *   POST /facturas/cargar          → un solo archivo (Excel o PDF) — compatibilidad
 *   POST /facturas/cargar-multiple → varios archivos (Excel y/o PDF)
 *
 * CONSULTAS / COMBOS
 *   GET  /facturas/municipios      → municipios disponibles en BD
 *   GET  /facturas/estratos        → estratos disponibles en BD
 *   GET  /facturas/meses           → períodos disponibles en BD
 *
 * RESUMEN
 *   POST /facturas/resumen         → resumen estadístico sin generar archivo
 *
 * REPORTES (devuelven archivo binario para descarga)
 *   POST /facturas/reporte         → Excel o PDF según filtros.tipoArchivo
 *   POST /facturas/reporte-ambos   → ZIP con Excel + PDF
 *
 * TOTALES (respuesta JSON, sin archivo)
 *   POST /facturas/totales-estrato
 *   POST /facturas/totales-municipio
 *   POST /facturas/detalle-vivienda
 */
@RestController
@RequestMapping("/facturas")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"},
             allowCredentials = "true")
public class FacturaController {

    @Autowired
    private FacturaService facturaService;

    @Autowired
    private ArchivoProcessorService archivoProcessorService;

    @Autowired
    private ReporteGeneradorService reporteGeneradorService;

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // ══════════════════════════════════════════════════════════════════════════
    // CARGA DE ARCHIVOS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Carga un único archivo (Excel o PDF).
     * Mantiene la firma anterior para compatibilidad con el frontend existente.
     *
     * @param archivo    archivo a cargar (multipart/form-data)
     * @param tipo       "base" o "extra" (default: "extra")
     */
    @PostMapping("/cargar")
    public ResponseEntity<?> cargarArchivo(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "tipo", defaultValue = "extra") String tipo) {

        try {
            ArchivoProcessorService.ResultadoCarga resultado =
                    archivoProcessorService.procesarArchivos(List.of(archivo), tipo);
            return ResponseEntity.ok(resultado);

        } catch (EpqException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorDto(e.getErrorCode().getCodigo(),
                            e.getMessage(), e.getDetalle()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorDto("ERR_999", "Error procesando el archivo: " + e.getMessage()));
        }
    }

    /**
     * Carga múltiples archivos en una sola petición.
     * Acepta mezcla de Excel (.xls, .xlsx) y PDF (.pdf).
     *
     * @param archivos   lista de archivos (campo "archivos[]" en el formulario)
     * @param tipo       "base" o "extra"
     */
    @PostMapping("/cargar-multiple")
    public ResponseEntity<?> cargarMultiplesArchivos(
            @RequestParam("archivos") MultipartFile[] archivos,
            @RequestParam(value = "tipo", defaultValue = "extra") String tipo) {

        if (archivos == null || archivos.length == 0) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorDto("ERR_002", "No se enviaron archivos para procesar"));
        }

        try {
            ArchivoProcessorService.ResultadoCarga resultado =
                    archivoProcessorService.procesarArchivos(Arrays.asList(archivos), tipo);

            // Si hay archivos con error pero otros procesados → 207 Multi-Status
            HttpStatus status = resultado.getArchivosConError() > 0
                    && resultado.getTotalRegistrosProcesados() > 0
                    ? HttpStatus.MULTI_STATUS
                    : HttpStatus.OK;

            return ResponseEntity.status(status).body(resultado);

        } catch (EpqException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorDto(e.getErrorCode().getCodigo(),
                            e.getMessage(), e.getDetalle()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorDto("ERR_999", "Error procesando archivos: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // CONSULTAS / COMBOS
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lista municipios disponibles en la base de datos.
     * Parámetros opcionales: tipoFuente, archivo (nombre de archivo).
     */
    @GetMapping("/municipios")
    public ResponseEntity<?> obtenerMunicipios(
            @RequestParam(required = false) String tipoFuente,
            @RequestParam(required = false) String archivo) {

        try {
            List<String> archivos = archivo != null && !archivo.isBlank()
                    ? List.of(archivo) : null;
            return ResponseEntity.ok(facturaService.obtenerMunicipios(tipoFuente, archivos));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorDto("ERR_999", e.getMessage()));
        }
    }

    /**
     * Lista estratos disponibles en la base de datos.
     */
    @GetMapping("/estratos")
    public ResponseEntity<?> obtenerEstratos(
            @RequestParam(required = false) String tipoFuente,
            @RequestParam(required = false) String archivo) {

        try {
            List<String> archivos = archivo != null && !archivo.isBlank()
                    ? List.of(archivo) : null;
            return ResponseEntity.ok(facturaService.obtenerEstratos(tipoFuente, archivos));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorDto("ERR_999", e.getMessage()));
        }
    }

    /**
     * Lista períodos disponibles en formato "YYYY-MM".
     */
    @GetMapping("/meses")
    public ResponseEntity<?> obtenerMeses(
            @RequestParam(required = false) String tipoFuente,
            @RequestParam(required = false) String archivo) {

        try {
            List<String> archivos = archivo != null && !archivo.isBlank()
                    ? List.of(archivo) : null;
            return ResponseEntity.ok(facturaService.obtenerMeses(tipoFuente, archivos));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorDto("ERR_999", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // RESUMEN ESTADÍSTICO (sin archivo)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Devuelve un resumen estadístico JSON sin generar archivo.
     * Útil para mostrar totales en el frontend antes de descargar.
     */
    @PostMapping("/resumen")
    public ResponseEntity<?> obtenerResumen(@RequestBody FiltroReporteDto filtros) {
        try {
            var facturas = facturaService.consultarConFiltros(filtros);
            if (facturas.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "totalFacturas", 0,
                        "totalGeneral", BigDecimal.ZERO,
                        "mensaje", "No hay datos para los filtros seleccionados"));
            }
            Map<String, Object> resumen = facturaService.calcularResumen(facturas, filtros);
            resumen.put("totalFacturas", facturas.size());
            return ResponseEntity.ok(resumen);
        } catch (EpqException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorDto(e.getErrorCode().getCodigo(),
                            e.getMessage(), e.getDetalle()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorDto("ERR_999", e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // REPORTES (respuesta binaria para descarga)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Genera el reporte en el formato especificado en filtros.tipoArchivo.
     * - "xlsx" → Excel con hojas por tipo de reporte
     * - "pdf"  → PDF con secciones
     *
     * Si filtros.ambosFormatos = true, usar /reporte-ambos.
     */
    @PostMapping("/reporte")
    public ResponseEntity<?> generarReporte(@RequestBody FiltroReporteDto filtros) {
        try {
            String formato = filtros.getTipoArchivo() != null
                    ? filtros.getTipoArchivo().toLowerCase() : "xlsx";

            byte[] archivo;
            String contentType;
            String extension;

            if ("pdf".equals(formato)) {
                archivo = reporteGeneradorService.generarPdf(filtros);
                contentType = MediaType.APPLICATION_PDF_VALUE;
                extension = "pdf";
            } else {
                archivo = reporteGeneradorService.generarExcel(filtros);
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                extension = "xlsx";
            }

            String nombreArchivo = "reporte_epq_" +
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) +
                    "." + extension;

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + nombreArchivo + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .body(archivo);

        } catch (EpqException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorDto(e.getErrorCode().getCodigo(),
                            e.getMessage(), e.getDetalle()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorDto("ERR_404", "Error generando el reporte: " + e.getMessage()));
        }
    }

    /**
     * Genera ambos formatos (Excel + PDF) y los empaqueta en un ZIP.
     * El frontend descarga un único archivo .zip con los dos reportes dentro.
     */
    @PostMapping("/reporte-ambos")
    public ResponseEntity<?> generarReporteAmbosFormatos(@RequestBody FiltroReporteDto filtros) {
        try {
            byte[] excel = reporteGeneradorService.generarExcel(filtros);
            byte[] pdf   = reporteGeneradorService.generarPdf(filtros);

            String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String nombreZip = "reporte_epq_" + fecha + ".zip";

            ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
                zos.putNextEntry(new ZipEntry("reporte_epq_" + fecha + ".xlsx"));
                zos.write(excel);
                zos.closeEntry();

                zos.putNextEntry(new ZipEntry("reporte_epq_" + fecha + ".pdf"));
                zos.write(pdf);
                zos.closeEntry();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + nombreZip + "\"")
                    .header(HttpHeaders.CONTENT_TYPE, "application/zip")
                    .body(zipOut.toByteArray());

        } catch (EpqException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorDto(e.getErrorCode().getCodigo(),
                            e.getMessage(), e.getDetalle()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorDto("ERR_404", "Error generando reportes: " + e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // TOTALES JSON (sin archivo, para tablas del frontend)
    // ══════════════════════════════════════════════════════════════════════════

    @PostMapping("/totales-estrato")
    public ResponseEntity<?> getTotalesPorEstrato(@RequestBody FiltroReporteDto filtros) {
        try {
            return ResponseEntity.ok(facturaService.obtenerTotalesPorEstrato(filtros));
        } catch (EpqException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorDto(e.getErrorCode().getCodigo(), e.getMessage(), e.getDetalle()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorDto("ERR_999", e.getMessage()));
        }
    }

    @PostMapping("/totales-municipio")
    public ResponseEntity<?> getTotalesPorMunicipio(@RequestBody FiltroReporteDto filtros) {
        try {
            return ResponseEntity.ok(facturaService.obtenerTotalesPorMunicipio(filtros));
        } catch (EpqException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorDto(e.getErrorCode().getCodigo(), e.getMessage(), e.getDetalle()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorDto("ERR_999", e.getMessage()));
        }
    }

    @PostMapping("/detalle-vivienda")
    public ResponseEntity<?> getDetallePorVivienda(@RequestBody FiltroReporteDto filtros) {
        try {
            return ResponseEntity.ok(facturaService.obtenerDetallePorVivienda(filtros));
        } catch (EpqException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorDto(e.getErrorCode().getCodigo(), e.getMessage(), e.getDetalle()));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorDto("ERR_999", e.getMessage()));
        }
    }
}
