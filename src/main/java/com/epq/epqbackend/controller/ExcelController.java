package com.epq.epqbackend.controller;

import com.epq.epqbackend.dto.ApiErrorDto;
import com.epq.epqbackend.model.ArchivoExcel;
import com.epq.epqbackend.repository.ArchivoExcelRepository;
import com.epq.epqbackend.repository.FacturaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Controller para gestión de archivos cargados al sistema.
 *
 * Endpoints:
 *   GET    /excel              → lista todos los archivos registrados
 *   GET    /excel/{nombre}     → detalle de un archivo + estadísticas de sus datos
 *   DELETE /excel/{nombre}     → elimina el registro del archivo y sus facturas asociadas
 *   GET    /excel/tipos        → lista tipos de fuente disponibles ("base", "extra")
 *   GET    /excel/municipios   → municipios disponibles en un archivo específico
 *   GET    /excel/meses        → períodos disponibles en un archivo específico
 */
@RestController
@RequestMapping("/excel")
@CrossOrigin(origins = {"http://localhost:4200", "http://localhost:3000"},
             allowCredentials = "true")
public class ExcelController {

    @Autowired
    private ArchivoExcelRepository archivoExcelRepository;

    @Autowired
    private FacturaRepository facturaRepository;

    // ── Listar todos los archivos ──────────────────────────────────────────────

    /**
     * Lista todos los archivos registrados.
     * Parámetro opcional: tipo ("base" o "extra") para filtrar.
     */
    @GetMapping
    public ResponseEntity<List<ArchivoExcel>> listarArchivos(
            @RequestParam(required = false) String tipo) {

        List<ArchivoExcel> archivos = tipo != null && !tipo.isBlank()
                ? archivoExcelRepository.findByTipo(tipo)
                : archivoExcelRepository.findAll();

        // Ordenar por fecha de carga descendente
        archivos.sort((a, b) -> {
            if (a.getFechaCarga() == null) return 1;
            if (b.getFechaCarga() == null) return -1;
            return b.getFechaCarga().compareTo(a.getFechaCarga());
        });

        return ResponseEntity.ok(archivos);
    }

    // ── Detalle de un archivo ──────────────────────────────────────────────────

    /**
     * Devuelve el registro del archivo junto con estadísticas de las facturas
     * que provienen de ese archivo (municipios, estratos, períodos, totales).
     */
    @GetMapping("/{nombre}")
    public ResponseEntity<?> detalle(@PathVariable String nombre) {
        ArchivoExcel archivo = archivoExcelRepository
                .findAll()
                .stream()
                .filter(a -> nombre.equals(a.getNombreArchivo()))
                .findFirst()
                .orElse(null);

        if (archivo == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ApiErrorDto("ERR_404",
                            "Archivo no encontrado: " + nombre));
        }

        // Enriquecer con datos de las facturas asociadas
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("archivo", archivo);

        try {
            List<String> municipios = facturaRepository.findMunicipiosDisponibles(
                    archivo.getTipo(), List.of(nombre));
            List<Integer> estratos = facturaRepository.findEstratoDisponibles(
                    archivo.getTipo(), List.of(nombre));
            List<String> meses = facturaRepository.findMesesDisponibles(
                    archivo.getTipo(), List.of(nombre));

            detalle.put("municipiosDisponibles", municipios);
            detalle.put("estratosDisponibles", estratos);
            detalle.put("mesesDisponibles", meses);
        } catch (Exception e) {
            // No bloquear si falla la consulta de estadísticas
            detalle.put("estadisticasError", "No se pudieron cargar las estadísticas");
        }

        return ResponseEntity.ok(detalle);
    }

    // ── Eliminar archivo ───────────────────────────────────────────────────────

    /**
     * Elimina el registro del archivo y todas las facturas asociadas a él.
     * Parámetro opcional: tipoFuente para precisar qué facturas eliminar.
     */
    @DeleteMapping("/{nombre}")
    public ResponseEntity<?> eliminarArchivo(
            @PathVariable String nombre,
            @RequestParam(required = false) String tipoFuente) {

        try {
            // Eliminar facturas asociadas
            ArchivoExcel archivo = archivoExcelRepository
                    .findAll().stream()
                    .filter(a -> nombre.equals(a.getNombreArchivo()))
                    .findFirst().orElse(null);

            if (archivo != null) {
                String tipo = tipoFuente != null ? tipoFuente : archivo.getTipo();
                if (tipo != null) {
                    facturaRepository.deleteByNombreArchivoOrigenAndTipoFuente(nombre, tipo);
                }
            }

            // Eliminar registro del archivo
            archivoExcelRepository.deleteByNombreArchivo(nombre);

            return ResponseEntity.ok(Map.of(
                    "mensaje", "Archivo '" + nombre + "' y sus facturas eliminados correctamente",
                    "nombre", nombre));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ApiErrorDto("ERR_999",
                            "Error eliminando el archivo: " + e.getMessage()));
        }
    }

    // ── Tipos de fuente disponibles ────────────────────────────────────────────

    /**
     * Devuelve los tipos de fuente distintos cargados en el sistema.
     */
    @GetMapping("/tipos")
    public ResponseEntity<?> listarTipos() {
        try {
            List<String> tipos = archivoExcelRepository.findAll()
                    .stream()
                    .map(ArchivoExcel::getTipo)
                    .filter(t -> t != null && !t.isBlank())
                    .distinct()
                    .sorted()
                    .toList();
            return ResponseEntity.ok(tipos);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorDto("ERR_999", e.getMessage()));
        }
    }

    // ── Municipios de un archivo específico ───────────────────────────────────

    /**
     * Devuelve los municipios disponibles en un archivo específico.
     * Útil para poblar el combo de municipios en el frontend cuando el usuario
     * selecciona un archivo antes de generar el reporte.
     */
    @GetMapping("/{nombre}/municipios")
    public ResponseEntity<?> municipiosPorArchivo(@PathVariable String nombre) {
        try {
            ArchivoExcel archivo = archivoExcelRepository.findAll().stream()
                    .filter(a -> nombre.equals(a.getNombreArchivo()))
                    .findFirst().orElse(null);

            String tipo = archivo != null ? archivo.getTipo() : null;
            List<String> municipios = facturaRepository.findMunicipiosDisponibles(
                    tipo, List.of(nombre));
            return ResponseEntity.ok(municipios);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorDto("ERR_999", e.getMessage()));
        }
    }

    // ── Períodos de un archivo específico ─────────────────────────────────────

    /**
     * Devuelve los períodos (YYYY-MM) disponibles en un archivo específico.
     */
    @GetMapping("/{nombre}/meses")
    public ResponseEntity<?> mesesPorArchivo(@PathVariable String nombre) {
        try {
            ArchivoExcel archivo = archivoExcelRepository.findAll().stream()
                    .filter(a -> nombre.equals(a.getNombreArchivo()))
                    .findFirst().orElse(null);

            String tipo = archivo != null ? archivo.getTipo() : null;
            List<String> meses = facturaRepository.findMesesDisponibles(
                    tipo, List.of(nombre));
            return ResponseEntity.ok(meses);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorDto("ERR_999", e.getMessage()));
        }
    }

    // ── Estratos de un archivo específico ─────────────────────────────────────

    /**
     * Devuelve los estratos disponibles en un archivo específico.
     */
    @GetMapping("/{nombre}/estratos")
    public ResponseEntity<?> estratosPorArchivo(@PathVariable String nombre) {
        try {
            ArchivoExcel archivo = archivoExcelRepository.findAll().stream()
                    .filter(a -> nombre.equals(a.getNombreArchivo()))
                    .findFirst().orElse(null);

            String tipo = archivo != null ? archivo.getTipo() : null;
            List<Integer> estratos = facturaRepository.findEstratoDisponibles(
                    tipo, List.of(nombre));
            return ResponseEntity.ok(estratos);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(new ApiErrorDto("ERR_999", e.getMessage()));
        }
    }
}
