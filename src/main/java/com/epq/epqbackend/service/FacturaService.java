package com.epq.epqbackend.service;

import com.epq.epqbackend.dto.FiltroReporteDto;
import com.epq.epqbackend.exception.EpqException;
import com.epq.epqbackend.exception.ErrorCode;
import com.epq.epqbackend.model.Factura;
import com.epq.epqbackend.repository.ArchivoExcelRepository;
import com.epq.epqbackend.repository.FacturaRepository;
import com.epq.epqbackend.util.MunicipioConstantes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de facturas.
 *
 * Responsabilidades:
 * - Consultar y filtrar facturas desde la base de datos.
 * - Preparar datos para los reportes (agrupaciones, totales).
 * - Proporcionar listas de valores únicos para los combos del frontend.
 *
 * El procesamiento de archivos fue delegado a ArchivoProcessorService.
 * La generación de Excel/PDF fue delegada a ReporteGeneradorService.
 * Esta clase se mantiene como fachada de consultas y compatibilidad.
 */
@Service
public class FacturaService {

    @Autowired
    private FacturaRepository facturaRepository;

    @Autowired
    private ArchivoExcelRepository archivoExcelRepository;

    // ── Valores únicos para los combos del frontend ────────────────────────────

    /**
     * Lista todos los municipios distintos presentes en la BD.
     * Opcionalmente filtra por tipo de fuente y/o archivo.
     */
    public List<String> obtenerMunicipios(String tipoFuente, List<String> archivos) {
        return facturaRepository.findMunicipiosDisponibles(tipoFuente, archivos);
    }

    /** Versión sin filtros (compatibilidad con FacturaController anterior). */
    public List<String> obtenerMunicipios() {
        return facturaRepository.findMunicipiosDisponibles(null, null);
    }

    /**
     * Lista todos los estratos distintos presentes en la BD.
     */
    public List<Integer> obtenerEstratos(String tipoFuente, List<String> archivos) {
        return facturaRepository.findEstratoDisponibles(tipoFuente, archivos);
    }

    /** Versión sin filtros (compatibilidad). */
    public List<Integer> obtenerEstratos() {
        return facturaRepository.findEstratoDisponibles(null, null);
    }

    /**
     * Lista todos los períodos (mesReferencia "YYYY-MM") distintos en la BD.
     */
    public List<String> obtenerMeses(String tipoFuente, List<String> archivos) {
        return facturaRepository.findMesesDisponibles(tipoFuente, archivos);
    }

    public List<String> obtenerMeses() {
        return facturaRepository.findMesesDisponibles(null, null);
    }

    // ── Consulta principal de facturas con filtros ─────────────────────────────

    /**
     * Aplica todos los filtros del FiltroReporteDto y devuelve la lista de facturas.
     * Combina filtros de BD con filtros en memoria (barrio, valorMin/Max).
     */
    public List<Factura> consultarConFiltros(FiltroReporteDto filtros) {
        // Normalizar municipios a mayúsculas
        List<String> municipios = normalizarMunicipios(filtros.getMunicipios());
        List<Integer> estratos  = filtros.getEstratosFiltro();
        List<String> archivos   = filtros.getNombreArchivosFiltro();
        String tipoFuente       = filtros.getTipoFuente();

        List<Factura> facturas;

        // Si hay meses específicos, usar query por mesReferencia
        if (filtros.getMesesReferencia() != null && !filtros.getMesesReferencia().isEmpty()) {
            facturas = facturaRepository.findConFiltrosMes(
                    municipios, estratos, filtros.getMesesReferencia(), tipoFuente, archivos);

        } else if (filtros.isSoloMorosos()) {
            facturas = facturaRepository.findMorosos(
                    filtros.getFechaInicio(), filtros.getFechaFin(),
                    municipios, estratos, tipoFuente, archivos);

        } else {
            facturas = facturaRepository.findConFiltros(
                    filtros.getFechaInicio(), filtros.getFechaFin(),
                    municipios, estratos, tipoFuente, archivos);
        }

        // Filtros adicionales en memoria
        facturas = filtrarEnMemoria(facturas, filtros);

        // Ordenar
        facturas = ordenar(facturas, filtros.getOrdenarPor(), filtros.getOrdenDireccion());

        return facturas;
    }

    // ── Totales y agrupaciones ─────────────────────────────────────────────────

    /**
     * Totales agrupados por estrato.
     * Devuelve mapa estrato → totalPagar.
     */
    public Map<Integer, BigDecimal> obtenerTotalesPorEstrato(FiltroReporteDto filtros) {
        List<Integer> estratos  = filtros.getEstratosFiltro();
        List<String> municipios = normalizarMunicipios(filtros.getMunicipios());

        List<Object[]> rows = facturaRepository.sumTotalPorEstrato(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios, estratos);

        Map<Integer, BigDecimal> resultado = new LinkedHashMap<>();
        for (Object[] row : rows) {
            resultado.put((Integer) row[0], (BigDecimal) row[1]);
        }
        return resultado;
    }

    /**
     * Totales agrupados por municipio.
     * Devuelve lista de mapas con municipio, totalPagar, totalMora, saldoMora.
     */
    public List<Map<String, Object>> obtenerTotalesPorMunicipio(FiltroReporteDto filtros) {
        List<String> municipios = normalizarMunicipios(filtros.getMunicipios());

        List<Object[]> rows = facturaRepository.sumTotalPorMunicipio(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios);

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("municipio",   row[0]);
            fila.put("totalPagar",  row[1]);
            fila.put("totalMora",   row[2]);
            fila.put("saldoMora",   row[3]);
            resultado.add(fila);
        }
        return resultado;
    }

    /**
     * Totales agrupados por mes de referencia.
     */
    public Map<String, BigDecimal> obtenerTotalesPorMes(FiltroReporteDto filtros) {
        List<String> municipios = normalizarMunicipios(filtros.getMunicipios());
        List<Integer> estratos  = filtros.getEstratosFiltro();
        List<String> archivos   = filtros.getNombreArchivosFiltro();

        List<Object[]> rows = facturaRepository.sumTotalPorMes(municipios, estratos, archivos);

        Map<String, BigDecimal> resultado = new LinkedHashMap<>();
        for (Object[] row : rows) {
            resultado.put((String) row[0], (BigDecimal) row[1]);
        }
        return resultado;
    }

    /**
     * Detalle por vivienda: dirección, barrio, contrato, valores.
     */
    public List<Map<String, Object>> obtenerDetallePorVivienda(FiltroReporteDto filtros) {
        List<String> municipios = normalizarMunicipios(filtros.getMunicipios());
        List<Integer> estratos  = filtros.getEstratosFiltro();

        List<Object[]> rows = facturaRepository.findDetalleVivienda(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios, estratos);

        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Object[] row : rows) {
            Map<String, Object> fila = new LinkedHashMap<>();
            fila.put("municipio",         row[0]);
            fila.put("estrato",           row[1]);
            fila.put("direccionServicio", row[2]);
            fila.put("barrio",            row[3]);
            fila.put("numeroContrato",    row[4]);
            fila.put("valorFactura",      row[5]);
            fila.put("valorMora",         row[6]);
            fila.put("saldoMora",         row[7]);
            fila.put("totalPagar",        row[8]);
            fila.put("estadoPago",        row[9]);
            fila.put("mesReferencia",     row[10]);
            resultado.add(fila);
        }
        return resultado;
    }

    // ── Resumen general ────────────────────────────────────────────────────────

    /**
     * Calcula el resumen estadístico a partir de una lista de facturas ya filtradas.
     * Usado por ReporteGeneradorService.
     */
    public Map<String, Object> calcularResumen(List<Factura> facturas, FiltroReporteDto filtros) {
        Map<String, Object> resumen = new LinkedHashMap<>();

        BigDecimal totalGeneral = facturas.stream()
                .map(f -> f.getTotalPagar() != null ? f.getTotalPagar() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        resumen.put("totalGeneral", totalGeneral);

        BigDecimal totalMora = facturas.stream()
                .map(f -> f.getValorMora() != null ? f.getValorMora() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        resumen.put("totalMora", totalMora);

        BigDecimal totalSaldoMora = facturas.stream()
                .map(f -> f.getSaldoMora() != null ? f.getSaldoMora() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        resumen.put("totalSaldoMora", totalSaldoMora);

        resumen.put("totalFacturas", facturas.size());

        long totalMorosos = facturas.stream().filter(Factura::tieneMora).count();
        resumen.put("totalMorosos", totalMorosos);

        // Agrupaciones opcionales
        if (filtros.isAgruparPorMunicipio() || filtros.isMostrarTotalesPorMunicipio()) {
            Map<String, BigDecimal> porMunicipio = facturas.stream()
                    .collect(Collectors.groupingBy(
                            Factura::getMunicipio,
                            LinkedHashMap::new,
                            Collectors.mapping(
                                    f -> f.getTotalPagar() != null ? f.getTotalPagar() : BigDecimal.ZERO,
                                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
            resumen.put("totalPorMunicipio", porMunicipio);
        }

        if (filtros.isAgruparPorMes()) {
            Map<String, BigDecimal> porMes = facturas.stream()
                    .filter(f -> f.getMesReferencia() != null)
                    .collect(Collectors.groupingBy(
                            Factura::getMesReferencia,
                            TreeMap::new,
                            Collectors.mapping(
                                    f -> f.getTotalPagar() != null ? f.getTotalPagar() : BigDecimal.ZERO,
                                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
            resumen.put("totalPorMes", porMes);
        }

        if (filtros.isAgruparPorEstrato() || filtros.isMostrarTotalesPorEstrato()) {
            Map<Integer, BigDecimal> porEstrato = facturas.stream()
                    .filter(f -> f.getEstrato() != null)
                    .collect(Collectors.groupingBy(
                            Factura::getEstrato,
                            TreeMap::new,
                            Collectors.mapping(
                                    f -> f.getTotalPagar() != null ? f.getTotalPagar() : BigDecimal.ZERO,
                                    Collectors.reducing(BigDecimal.ZERO, BigDecimal::add))));
            resumen.put("totalPorEstrato", porEstrato);
        }

        return resumen;
    }

    // ── Filtros en memoria ─────────────────────────────────────────────────────

    private List<Factura> filtrarEnMemoria(List<Factura> facturas, FiltroReporteDto filtros) {
        return facturas.stream()
                .filter(f -> filtros.getBarrio() == null || filtros.getBarrio().isBlank()
                        || (f.getBarrio() != null && f.getBarrio().equalsIgnoreCase(filtros.getBarrio())))
                .filter(f -> filtros.getEstadoPago() == null || filtros.getEstadoPago().isBlank()
                        || filtros.getEstadoPago().equalsIgnoreCase(f.getEstadoPago()))
                .filter(f -> filtros.getValorMinimo() == null
                        || (f.getTotalPagar() != null && f.getTotalPagar().compareTo(filtros.getValorMinimo()) >= 0))
                .filter(f -> filtros.getValorMaximo() == null
                        || (f.getTotalPagar() != null && f.getTotalPagar().compareTo(filtros.getValorMaximo()) <= 0))
                .collect(Collectors.toList());
    }

    private List<Factura> ordenar(List<Factura> facturas, String campo, String direccion) {
        if (campo == null || campo.isBlank()) return facturas;

        Comparator<Factura> comparator = switch (campo.toLowerCase()) {
            case "municipio" -> Comparator.comparing(
                    f -> f.getMunicipio() != null ? f.getMunicipio() : "");
            case "fecha"     -> Comparator.comparing(
                    f -> f.getFechaFactura() != null ? f.getFechaFactura() : LocalDate.MIN);
            case "valor"     -> Comparator.comparing(
                    f -> f.getTotalPagar() != null ? f.getTotalPagar() : BigDecimal.ZERO);
            case "estrato"   -> Comparator.comparing(
                    f -> f.getEstrato() != null ? f.getEstrato() : 0);
            case "mes"       -> Comparator.comparing(
                    f -> f.getMesReferencia() != null ? f.getMesReferencia() : "");
            default -> Comparator.comparing(f -> f.getFechaFactura() != null
                    ? f.getFechaFactura() : LocalDate.MIN);
        };

        if ("DESC".equalsIgnoreCase(direccion)) {
            comparator = comparator.reversed();
        }

        return facturas.stream().sorted(comparator).collect(Collectors.toList());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private List<String> normalizarMunicipios(List<String> municipios) {
        if (municipios == null || municipios.isEmpty()) return null;
        return municipios.stream()
                .filter(m -> m != null && !m.isBlank())
                .map(MunicipioConstantes::eliminarTildes)
                .map(String::toUpperCase)
                .distinct()
                .collect(Collectors.toList());
    }

    // ── Métodos de compatibilidad con FacturaController anterior ───────────────

    /**
     * @deprecated Usar ArchivoProcessorService.procesarArchivos()
     */
    @Deprecated
    public void procesarExcel(org.springframework.web.multipart.MultipartFile archivo,
                               String tipoFuente, String nombreArchivo) throws Exception {
        throw new EpqException(ErrorCode.ERROR_INTERNO,
                "procesarExcel() fue reemplazado por ArchivoProcessorService. " +
                "Usa POST /api/facturas/cargar con multipart multiple.");
    }

    /**
     * Compatibilidad: obtener totales por estrato con la firma del controller anterior.
     */
    public Map<Integer, BigDecimal> obtenerTotalesPorEstrato_legacy(FiltroReporteDto filtros) {
        return obtenerTotalesPorEstrato(filtros);
    }
}

