package com.epq.epqbackend.repository;

import com.epq.epqbackend.model.Factura;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface FacturaRepository extends JpaRepository<Factura, Long> {

    // ── Consultas simples ──────────────────────────────────────────────────────

    List<Factura> findByMunicipioIn(List<String> municipios);

    List<Factura> findByFechaFacturaBetween(LocalDate inicio, LocalDate fin);

    // ── Consulta principal con filtros combinados ──────────────────────────────

    /**
     * Filtro principal. Todos los parámetros son opcionales (null = sin filtro).
     * Soporta múltiples municipios, múltiples estratos y múltiples archivos fuente.
     */
    @Query("SELECT f FROM Factura f WHERE " +
            "(:inicio      IS NULL OR f.fechaFactura >= :inicio) AND " +
            "(:fin         IS NULL OR f.fechaFactura <= :fin) AND " +
            "(:municipios  IS NULL OR f.municipio IN :municipios) AND " +
            "(:estratos    IS NULL OR f.estrato IN :estratos) AND " +
            "(:tipoFuente  IS NULL OR f.tipoFuente = :tipoFuente) AND " +
            "(:archivos    IS NULL OR f.nombreArchivoOrigen IN :archivos)")
    List<Factura> findConFiltros(
            @Param("inicio")     LocalDate inicio,
            @Param("fin")        LocalDate fin,
            @Param("municipios") List<String> municipios,
            @Param("estratos")   List<Integer> estratos,
            @Param("tipoFuente") String tipoFuente,
            @Param("archivos")   List<String> archivos);

    /**
     * Filtro con soporte de períodos específicos (campo mesReferencia = "YYYY-MM").
     */
    @Query("SELECT f FROM Factura f WHERE " +
            "(:municipios IS NULL OR f.municipio IN :municipios) AND " +
            "(:estratos   IS NULL OR f.estrato IN :estratos) AND " +
            "(:meses      IS NULL OR f.mesReferencia IN :meses) AND " +
            "(:tipoFuente IS NULL OR f.tipoFuente = :tipoFuente) AND " +
            "(:archivos   IS NULL OR f.nombreArchivoOrigen IN :archivos)")
    List<Factura> findConFiltrosMes(
            @Param("municipios") List<String> municipios,
            @Param("estratos")   List<Integer> estratos,
            @Param("meses")      List<String> meses,
            @Param("tipoFuente") String tipoFuente,
            @Param("archivos")   List<String> archivos);

    /** Solo morosos, con los mismos filtros opcionales. */
    @Query("SELECT f FROM Factura f WHERE " +
            "(f.valorMora > 0 OR f.saldoMora > 0 OR f.estadoPago = 'VENCIDO') AND " +
            "(:inicio      IS NULL OR f.fechaFactura >= :inicio) AND " +
            "(:fin         IS NULL OR f.fechaFactura <= :fin) AND " +
            "(:municipios  IS NULL OR f.municipio IN :municipios) AND " +
            "(:estratos    IS NULL OR f.estrato IN :estratos) AND " +
            "(:tipoFuente  IS NULL OR f.tipoFuente = :tipoFuente) AND " +
            "(:archivos    IS NULL OR f.nombreArchivoOrigen IN :archivos)")
    List<Factura> findMorosos(
            @Param("inicio")     LocalDate inicio,
            @Param("fin")        LocalDate fin,
            @Param("municipios") List<String> municipios,
            @Param("estratos")   List<Integer> estratos,
            @Param("tipoFuente") String tipoFuente,
            @Param("archivos")   List<String> archivos);

    // ── Consultas de valores únicos para combos del frontend ──────────────────

    @Query("SELECT DISTINCT f.municipio FROM Factura f WHERE " +
            "(:tipoFuente IS NULL OR f.tipoFuente = :tipoFuente) AND " +
            "(:archivos   IS NULL OR f.nombreArchivoOrigen IN :archivos) " +
            "ORDER BY f.municipio")
    List<String> findMunicipiosDisponibles(
            @Param("tipoFuente") String tipoFuente,
            @Param("archivos")   List<String> archivos);

    @Query("SELECT DISTINCT f.estrato FROM Factura f WHERE f.estrato IS NOT NULL AND " +
            "(:tipoFuente IS NULL OR f.tipoFuente = :tipoFuente) AND " +
            "(:archivos   IS NULL OR f.nombreArchivoOrigen IN :archivos) " +
            "ORDER BY f.estrato")
    List<Integer> findEstratoDisponibles(
            @Param("tipoFuente") String tipoFuente,
            @Param("archivos")   List<String> archivos);

    @Query("SELECT DISTINCT f.mesReferencia FROM Factura f WHERE f.mesReferencia IS NOT NULL AND " +
            "(:tipoFuente IS NULL OR f.tipoFuente = :tipoFuente) AND " +
            "(:archivos   IS NULL OR f.nombreArchivoOrigen IN :archivos) " +
            "ORDER BY f.mesReferencia")
    List<String> findMesesDisponibles(
            @Param("tipoFuente") String tipoFuente,
            @Param("archivos")   List<String> archivos);

    // ── Consultas de compatibilidad (mantienen firmas anteriores) ─────────────

    /** @deprecated Usar {@link #findMunicipiosDisponibles} */
    @Deprecated
    @Query("SELECT DISTINCT f.municipio FROM Factura f WHERE " +
            "(:tipoFuente IS NULL OR f.tipoFuente = :tipoFuente) AND " +
            "(:nombreArchivo IS NULL OR f.nombreArchivoOrigen = :nombreArchivo) " +
            "ORDER BY f.municipio")
    List<String> findMunicipiosByFuenteAndArchivo(
            @Param("tipoFuente")    String tipoFuente,
            @Param("nombreArchivo") String nombreArchivo);

    /** @deprecated Usar {@link #findEstratoDisponibles} */
    @Deprecated
    @Query("SELECT DISTINCT f.estrato FROM Factura f WHERE f.estrato IS NOT NULL AND " +
            "(:tipoFuente IS NULL OR f.tipoFuente = :tipoFuente) AND " +
            "(:nombreArchivo IS NULL OR f.nombreArchivoOrigen = :nombreArchivo) " +
            "ORDER BY f.estrato")
    List<Integer> findEstratosByFuenteAndArchivo(
            @Param("tipoFuente")    String tipoFuente,
            @Param("nombreArchivo") String nombreArchivo);

    // ── Eliminación por archivo ────────────────────────────────────────────────

    @Modifying
    @Transactional
    @Query("DELETE FROM Factura f WHERE f.nombreArchivoOrigen = :nombreArchivo AND f.tipoFuente = :tipoFuente")
    void deleteByNombreArchivoOrigenAndTipoFuente(
            @Param("nombreArchivo") String nombreArchivo,
            @Param("tipoFuente")    String tipoFuente);

    // ── Agregaciones para reportes ─────────────────────────────────────────────

    /** Total por estrato (estratos 1-6). */
    @Query("SELECT f.estrato, SUM(f.totalPagar) FROM Factura f WHERE " +
            "f.estrato BETWEEN 1 AND 6 AND " +
            "(:inicio     IS NULL OR f.fechaFactura >= :inicio) AND " +
            "(:fin        IS NULL OR f.fechaFactura <= :fin) AND " +
            "(:municipios IS NULL OR f.municipio IN :municipios) AND " +
            "(:estratos   IS NULL OR f.estrato IN :estratos) " +
            "GROUP BY f.estrato ORDER BY f.estrato")
    List<Object[]> sumTotalPorEstrato(
            @Param("inicio")     LocalDate inicio,
            @Param("fin")        LocalDate fin,
            @Param("municipios") List<String> municipios,
            @Param("estratos")   List<Integer> estratos);

    /** Total por municipio. */
    @Query("SELECT f.municipio, SUM(f.totalPagar), SUM(f.valorMora), SUM(f.saldoMora) FROM Factura f WHERE " +
            "(:inicio     IS NULL OR f.fechaFactura >= :inicio) AND " +
            "(:fin        IS NULL OR f.fechaFactura <= :fin) AND " +
            "(:municipios IS NULL OR f.municipio IN :municipios) " +
            "GROUP BY f.municipio ORDER BY f.municipio")
    List<Object[]> sumTotalPorMunicipio(
            @Param("inicio")     LocalDate inicio,
            @Param("fin")        LocalDate fin,
            @Param("municipios") List<String> municipios);

    /** Total por mes de referencia. */
    @Query("SELECT f.mesReferencia, SUM(f.totalPagar), COUNT(f) FROM Factura f WHERE " +
            "(:municipios IS NULL OR f.municipio IN :municipios) AND " +
            "(:estratos   IS NULL OR f.estrato IN :estratos) AND " +
            "(:archivos   IS NULL OR f.nombreArchivoOrigen IN :archivos) " +
            "GROUP BY f.mesReferencia ORDER BY f.mesReferencia")
    List<Object[]> sumTotalPorMes(
            @Param("municipios") List<String> municipios,
            @Param("estratos")   List<Integer> estratos,
            @Param("archivos")   List<String> archivos);

    /** Detalle de vivienda con todos los campos relevantes. */
    @Query("SELECT f.municipio, f.estrato, f.direccionServicio, f.barrio, f.numeroContrato, " +
            "f.valorFactura, f.valorMora, f.saldoMora, f.totalPagar, f.estadoPago, f.mesReferencia " +
            "FROM Factura f WHERE " +
            "(:inicio     IS NULL OR f.fechaFactura >= :inicio) AND " +
            "(:fin        IS NULL OR f.fechaFactura <= :fin) AND " +
            "(:municipios IS NULL OR f.municipio IN :municipios) AND " +
            "(:estratos   IS NULL OR f.estrato IN :estratos)")
    List<Object[]> findDetalleVivienda(
            @Param("inicio")     LocalDate inicio,
            @Param("fin")        LocalDate fin,
            @Param("municipios") List<String> municipios,
            @Param("estratos")   List<Integer> estratos);

    // ── Métodos de compatibilidad con firma anterior ───────────────────────────

    /** @deprecated Usar {@link #sumTotalPorEstrato} */
    @Deprecated
    @Query("SELECT f.estrato, SUM(f.totalPagar) FROM Factura f WHERE " +
            "f.fechaFactura BETWEEN :inicio AND :fin AND " +
            "(:municipios IS NULL OR f.municipio IN :municipios) AND " +
            "f.estrato BETWEEN 1 AND 5 GROUP BY f.estrato ORDER BY f.estrato")
    List<Object[]> sumTotalByEstrato(
            @Param("inicio")     LocalDate inicio,
            @Param("fin")        LocalDate fin,
            @Param("municipios") List<String> municipios);

    /** @deprecated Usar {@link #sumTotalPorMunicipio} */
    @Deprecated
    @Query("SELECT f.municipio, SUM(f.totalPagar) FROM Factura f WHERE " +
            "f.fechaFactura BETWEEN :inicio AND :fin AND " +
            "(:municipios IS NULL OR f.municipio IN :municipios) " +
            "GROUP BY f.municipio ORDER BY f.municipio")
    List<Object[]> sumTotalByMunicipio(
            @Param("inicio")     LocalDate inicio,
            @Param("fin")        LocalDate fin,
            @Param("municipios") List<String> municipios);
}
