package com.epq.epqbackend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Filtros para la generación de reportes.
 *
 * Principales ampliaciones respecto a la versión anterior:
 * - estratos: ahora es List<Integer> para selección múltiple.
 * - mesesReferencia: lista de períodos "YYYY-MM" para filtrar sin rango de fechas.
 * - ambosFormatos: genera Excel Y PDF en la misma petición.
 * - tiposReporte: lista de tipos de reporte a incluir en el archivo.
 * - nombreArchivos: soporte para filtrar por múltiples archivos fuente.
 */
public class FiltroReporteDto {

    // ── Filtros de selección ───────────────────────────────────────────────────

    /** Uno o varios municipios. Null o lista vacía = todos los municipios. */
    private List<String> municipios;

    /**
     * Uno o varios estratos socioeconómicos (1-6).
     * Null o lista vacía = todos los estratos.
     * Reemplaza el campo Integer estrato anterior (se mantiene por compatibilidad).
     */
    private List<Integer> estratos;

    /**
     * @deprecated Usar {@link #estratos} (lista). Se mantiene por compatibilidad
     *             con clientes que envíen un único estrato.
     */
    @Deprecated
    private Integer estrato;

    /** Rango de fechas por fecha de factura. */
    private LocalDate fechaInicio;
    private LocalDate fechaFin;

    /**
     * Filtro por períodos específicos en formato "YYYY-MM".
     * Ejemplo: ["2024-01", "2024-02"]. Se usa en lugar de fechaInicio/fechaFin
     * cuando se quieren meses puntuales no necesariamente contiguos.
     */
    private List<String> mesesReferencia;

    /** Filtrar solo registros con mora o estado VENCIDO. */
    private boolean soloMorosos = false;

    /** Filtrar por barrio específico. */
    private String barrio;

    /** Filtrar por estado de pago: PENDIENTE, VENCIDO o PAGADO. */
    private String estadoPago;

    /** Rango de valor de factura. */
    private BigDecimal valorMinimo;
    private BigDecimal valorMaximo;

    // ── Archivos fuente ────────────────────────────────────────────────────────

    /**
     * Nombre del archivo fuente principal (compatibilidad con versión anterior).
     */
    private String nombreArchivo;

    /**
     * Múltiples archivos fuente para combinar datos en un mismo reporte.
     */
    private List<String> nombreArchivos;

    /** Tipo de fuente: "base" o "extra". */
    private String tipoFuente;

    // ── Opciones de agrupación ─────────────────────────────────────────────────

    private boolean agruparPorMunicipio = false;
    private boolean agruparPorMes       = false;
    private boolean agruparPorEstrato   = false;

    // ── Tipos de reporte a generar ────────────────────────────────────────────

    /**
     * Lista de tipos de reporte a incluir.
     * Valores posibles: DEUDORES_MOROSOS, TOTALES_MUNICIPIO, TOTALES_ESTRATO,
     *                   DETALLE_VIVIENDA, AGRUPADO_MES, COMBINADO
     */
    private List<String> tiposReporte;

    /** Incluir datos de mora en el reporte. */
    private boolean incluirMora = false;

    /** Mostrar detalle por vivienda (dirección, barrio, contrato). */
    private boolean mostrarDetallePorVivienda = false;

    /** Incluir hoja/sección de totales por estrato. */
    private boolean mostrarTotalesPorEstrato = false;

    /** Incluir hoja/sección de totales por municipio. */
    private boolean mostrarTotalesPorMunicipio = false;

    // ── Opciones de exportación ────────────────────────────────────────────────

    /**
     * Formato del archivo generado: "xlsx" o "pdf".
     */
    private String tipoArchivo = "xlsx";

    /**
     * Si es true, genera ambos formatos (Excel y PDF) en la misma petición.
     * El controlador devuelve un ZIP con los dos archivos.
     */
    private boolean ambosFormatos = false;

    // ── Ordenación ────────────────────────────────────────────────────────────

    /** Campo por el que ordenar: "municipio", "fecha", "valor", "estrato". */
    private String ordenarPor;

    /** Dirección del orden: "ASC" o "DESC". */
    private String ordenDireccion;

    // ── Getters y Setters ──────────────────────────────────────────────────────

    public List<String> getMunicipios() { return municipios; }
    public void setMunicipios(List<String> municipios) { this.municipios = municipios; }

    public List<Integer> getEstratos() { return estratos; }
    public void setEstratos(List<Integer> estratos) { this.estratos = estratos; }

    @Deprecated
    public Integer getEstrato() { return estrato; }
    @Deprecated
    public void setEstrato(Integer estrato) {
        this.estrato = estrato;
        // Mantener sincronía: si llegan por el campo legacy, agregarlo a la lista
        if (estrato != null && (this.estratos == null || this.estratos.isEmpty())) {
            this.estratos = List.of(estrato);
        }
    }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public List<String> getMesesReferencia() { return mesesReferencia; }
    public void setMesesReferencia(List<String> mesesReferencia) { this.mesesReferencia = mesesReferencia; }

    public boolean isSoloMorosos() { return soloMorosos; }
    public void setSoloMorosos(boolean soloMorosos) { this.soloMorosos = soloMorosos; }

    public String getBarrio() { return barrio; }
    public void setBarrio(String barrio) { this.barrio = barrio; }

    public String getEstadoPago() { return estadoPago; }
    public void setEstadoPago(String estadoPago) { this.estadoPago = estadoPago; }

    public BigDecimal getValorMinimo() { return valorMinimo; }
    public void setValorMinimo(BigDecimal valorMinimo) { this.valorMinimo = valorMinimo; }

    public BigDecimal getValorMaximo() { return valorMaximo; }
    public void setValorMaximo(BigDecimal valorMaximo) { this.valorMaximo = valorMaximo; }

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    public List<String> getNombreArchivos() { return nombreArchivos; }
    public void setNombreArchivos(List<String> nombreArchivos) { this.nombreArchivos = nombreArchivos; }

    public String getTipoFuente() { return tipoFuente; }
    public void setTipoFuente(String tipoFuente) { this.tipoFuente = tipoFuente; }

    public boolean isAgruparPorMunicipio() { return agruparPorMunicipio; }
    public void setAgruparPorMunicipio(boolean agruparPorMunicipio) { this.agruparPorMunicipio = agruparPorMunicipio; }

    public boolean isAgruparPorMes() { return agruparPorMes; }
    public void setAgruparPorMes(boolean agruparPorMes) { this.agruparPorMes = agruparPorMes; }

    public boolean isAgruparPorEstrato() { return agruparPorEstrato; }
    public void setAgruparPorEstrato(boolean agruparPorEstrato) { this.agruparPorEstrato = agruparPorEstrato; }

    public List<String> getTiposReporte() { return tiposReporte; }
    public void setTiposReporte(List<String> tiposReporte) { this.tiposReporte = tiposReporte; }

    public boolean isIncluirMora() { return incluirMora; }
    public void setIncluirMora(boolean incluirMora) { this.incluirMora = incluirMora; }

    public boolean isMostrarDetallePorVivienda() { return mostrarDetallePorVivienda; }
    public void setMostrarDetallePorVivienda(boolean mostrarDetallePorVivienda) { this.mostrarDetallePorVivienda = mostrarDetallePorVivienda; }

    public boolean isMostrarTotalesPorEstrato() { return mostrarTotalesPorEstrato; }
    public void setMostrarTotalesPorEstrato(boolean mostrarTotalesPorEstrato) { this.mostrarTotalesPorEstrato = mostrarTotalesPorEstrato; }

    public boolean isMostrarTotalesPorMunicipio() { return mostrarTotalesPorMunicipio; }
    public void setMostrarTotalesPorMunicipio(boolean mostrarTotalesPorMunicipio) { this.mostrarTotalesPorMunicipio = mostrarTotalesPorMunicipio; }

    public String getTipoArchivo() { return tipoArchivo; }
    public void setTipoArchivo(String tipoArchivo) { this.tipoArchivo = tipoArchivo; }

    public boolean isAmbosFormatos() { return ambosFormatos; }
    public void setAmbosFormatos(boolean ambosFormatos) { this.ambosFormatos = ambosFormatos; }

    public String getOrdenarPor() { return ordenarPor; }
    public void setOrdenarPor(String ordenarPor) { this.ordenarPor = ordenarPor; }

    public String getOrdenDireccion() { return ordenDireccion; }
    public void setOrdenDireccion(String ordenDireccion) { this.ordenDireccion = ordenDireccion; }

    // ── Método de utilidad ─────────────────────────────────────────────────────

    /**
     * Devuelve la lista efectiva de estratos a usar en la consulta,
     * combinando el campo legacy {@code estrato} y la nueva lista {@code estratos}.
     */
    public List<Integer> getEstratosFiltro() {
        if (estratos != null && !estratos.isEmpty()) return estratos;
        if (estrato  != null) return List.of(estrato);
        return null;
    }

    /**
     * Devuelve los nombres de archivos fuente a usar,
     * combinando el campo legacy {@code nombreArchivo} y la nueva lista {@code nombreArchivos}.
     */
    public List<String> getNombreArchivosFiltro() {
        if (nombreArchivos != null && !nombreArchivos.isEmpty()) return nombreArchivos;
        if (nombreArchivo  != null && !nombreArchivo.isBlank())  return List.of(nombreArchivo);
        return null;
    }
}
