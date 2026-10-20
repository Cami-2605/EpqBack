package com.epq.epqbackend.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Filtros para los informes analíticos del sistema EPQ.
 *
 * Estos informes van más allá del reporte básico de facturas:
 * trabajan con agregaciones, rankings, proyecciones y distribuciones.
 */
public class FiltroInformeDto {

    /** Municipios a incluir. Null o vacío = todos. */
    private List<String> municipios;

    /** Estratos a incluir. Null o vacío = todos. */
    private List<Integer> estratos;

    /** Período de inicio para filtros por fecha. */
    private LocalDate fechaInicio;

    /** Período de fin para filtros por fecha. */
    private LocalDate fechaFin;

    /** Archivos fuente a considerar. Null o vacío = todos. */
    private List<String> nombreArchivos;

    /** Tipo de fuente: "base" o "extra". Null = todos. */
    private String tipoFuente;

    /**
     * Para el informe Top N morosos: cantidad de registros a devolver.
     * Default: 40.
     */
    private int topN = 40;

    /**
     * Tipo de informe a generar. Valores posibles:
     *   TOP_MOROSOS           — top N morosos por edad de mora y valor
     *   RECAUDO_GENERAL       — recaudo total agrupado por mes/municipio
     *   EDAD_MORA             — distribución de morosos por rango de meses
     *   RECAUDO_MUNICIPIO     — recaudo por municipio
     *   RECAUDO_SERVICIO      — recaudo por tipo de servicio (gas, agua, etc.)
     *   MOROSOS_POR_ESTRATO   — top morosos dentro de cada estrato
     *   PAGOS_POR_ESTRATO     — sumatoria de pagos estratos 1-2-3 (comparativa)
     *   RECAUDO_DIARIO        — recaudo agrupado por día
     *   PROYECCION            — promedio mensual y proyección a N meses
     *   TODOS                 — genera todos los informes anteriores en un mismo archivo
     */
    private String tipoInforme = "TODOS";

    /**
     * Para el informe de proyección: cuántos meses futuros proyectar.
     * Default: 3.
     */
    private int mesesProyeccion = 3;

    /**
     * Formato de salida: "xlsx" o "pdf". Default: "xlsx".
     */
    private String formato = "xlsx";

    // ── Getters y Setters ──────────────────────────────────────────────────────

    public List<String> getMunicipios() { return municipios; }
    public void setMunicipios(List<String> municipios) { this.municipios = municipios; }

    public List<Integer> getEstratos() { return estratos; }
    public void setEstratos(List<Integer> estratos) { this.estratos = estratos; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public LocalDate getFechaFin() { return fechaFin; }
    public void setFechaFin(LocalDate fechaFin) { this.fechaFin = fechaFin; }

    public List<String> getNombreArchivos() { return nombreArchivos; }
    public void setNombreArchivos(List<String> nombreArchivos) { this.nombreArchivos = nombreArchivos; }

    public String getTipoFuente() { return tipoFuente; }
    public void setTipoFuente(String tipoFuente) { this.tipoFuente = tipoFuente; }

    public int getTopN() { return topN > 0 ? topN : 40; }
    public void setTopN(int topN) { this.topN = topN; }

    public String getTipoInforme() { return tipoInforme; }
    public void setTipoInforme(String tipoInforme) { this.tipoInforme = tipoInforme; }

    public int getMesesProyeccion() { return mesesProyeccion > 0 ? mesesProyeccion : 3; }
    public void setMesesProyeccion(int mesesProyeccion) { this.mesesProyeccion = mesesProyeccion; }

    public String getFormato() { return formato != null ? formato : "xlsx"; }
    public void setFormato(String formato) { this.formato = formato; }
}
