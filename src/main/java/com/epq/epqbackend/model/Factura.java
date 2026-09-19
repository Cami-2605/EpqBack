package com.epq.epqbackend.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "facturas", indexes = {
        @Index(name = "idx_municipio",      columnList = "municipio"),
        @Index(name = "idx_fecha",          columnList = "fecha_factura"),
        @Index(name = "idx_estrato",        columnList = "estrato"),
        @Index(name = "idx_estado_pago",    columnList = "estado_pago"),
        @Index(name = "idx_archivo_origen", columnList = "nombre_archivo_origen"),
        @Index(name = "idx_mes_referencia", columnList = "mes_referencia")
})
public class Factura {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Ubicación ──────────────────────────────────────────────
    @Column(nullable = false, length = 100)
    private String municipio;

    @Column(length = 150)
    private String barrio;

    @Column(name = "direccion_servicio", length = 255)
    private String direccionServicio;

    @Column(name = "numero_contrato", length = 50)
    private String numeroContrato;

    // ── Servicio ───────────────────────────────────────────────
    @Column(name = "nombre_servicio", length = 150)
    private String nombreServicio;

    /** Estrato socioeconómico (1-6). Puede ser nulo si no está en la fuente. */
    private Integer estrato;

    // ── Fechas ─────────────────────────────────────────────────
    @Column(name = "fecha_factura", nullable = false)
    private LocalDate fechaFactura;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;

    /**
     * Mes de referencia de la factura en formato "YYYY-MM".
     * Sirve para agrupar por período sin depender de la fecha exacta.
     * Ejemplo: "2024-03"
     */
    @Column(name = "mes_referencia", length = 20)
    private String mesReferencia;

    // ── Valores monetarios ─────────────────────────────────────
    @Column(name = "valor_factura", precision = 15, scale = 2)
    private BigDecimal valorFactura;

    /**
     * Valor de mora: recargo por pago tardío incluido en la factura.
     */
    @Column(name = "valor_mora", precision = 15, scale = 2)
    private BigDecimal valorMora = BigDecimal.ZERO;

    /**
     * Saldo de mora acumulado: deuda pendiente de períodos anteriores.
     * Distinto a valorMora (que es el recargo del mes actual).
     */
    @Column(name = "saldo_mora", precision = 15, scale = 2)
    private BigDecimal saldoMora = BigDecimal.ZERO;

    @Column(name = "total_pagar", precision = 15, scale = 2)
    private BigDecimal totalPagar;

    // ── Estado ─────────────────────────────────────────────────
    /**
     * Estado de pago: PENDIENTE, VENCIDO o PAGADO.
     */
    @Column(name = "estado_pago", length = 20)
    private String estadoPago = "PENDIENTE";

    // ── Trazabilidad ───────────────────────────────────────────
    /** Tipo de fuente del archivo: "base" o "extra" */
    @Column(name = "tipo_fuente", length = 20)
    private String tipoFuente;

    /** Nombre del archivo del que proviene este registro */
    @Column(name = "nombre_archivo_origen", length = 255)
    private String nombreArchivoOrigen;

    /** Fecha en que se cargó el archivo */
    @Column(name = "fecha_carga")
    private LocalDate fechaCarga;

    /**
     * Tipo de archivo de origen: "EXCEL" o "PDF".
     * Útil para saber cómo fue extraída la información.
     */
    @Column(name = "tipo_archivo_origen", length = 10)
    private String tipoArchivoOrigen = "EXCEL";

    public Factura() {}

    // ── Getters y Setters ──────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMunicipio() { return municipio; }
    public void setMunicipio(String municipio) { this.municipio = municipio; }

    public String getBarrio() { return barrio; }
    public void setBarrio(String barrio) { this.barrio = barrio; }

    public String getDireccionServicio() { return direccionServicio; }
    public void setDireccionServicio(String direccionServicio) { this.direccionServicio = direccionServicio; }

    public String getNumeroContrato() { return numeroContrato; }
    public void setNumeroContrato(String numeroContrato) { this.numeroContrato = numeroContrato; }

    public String getNombreServicio() { return nombreServicio; }
    public void setNombreServicio(String nombreServicio) { this.nombreServicio = nombreServicio; }

    public Integer getEstrato() { return estrato; }
    public void setEstrato(Integer estrato) { this.estrato = estrato; }

    public LocalDate getFechaFactura() { return fechaFactura; }
    public void setFechaFactura(LocalDate fechaFactura) { this.fechaFactura = fechaFactura; }

    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    public String getMesReferencia() { return mesReferencia; }
    public void setMesReferencia(String mesReferencia) { this.mesReferencia = mesReferencia; }

    public BigDecimal getValorFactura() { return valorFactura; }
    public void setValorFactura(BigDecimal valorFactura) { this.valorFactura = valorFactura; }

    public BigDecimal getValorMora() { return valorMora; }
    public void setValorMora(BigDecimal valorMora) { this.valorMora = valorMora; }

    public BigDecimal getSaldoMora() { return saldoMora; }
    public void setSaldoMora(BigDecimal saldoMora) { this.saldoMora = saldoMora; }

    public BigDecimal getTotalPagar() { return totalPagar; }
    public void setTotalPagar(BigDecimal totalPagar) { this.totalPagar = totalPagar; }

    public String getEstadoPago() { return estadoPago; }
    public void setEstadoPago(String estadoPago) { this.estadoPago = estadoPago; }

    public String getTipoFuente() { return tipoFuente; }
    public void setTipoFuente(String tipoFuente) { this.tipoFuente = tipoFuente; }

    public String getNombreArchivoOrigen() { return nombreArchivoOrigen; }
    public void setNombreArchivoOrigen(String nombreArchivoOrigen) { this.nombreArchivoOrigen = nombreArchivoOrigen; }

    public LocalDate getFechaCarga() { return fechaCarga; }
    public void setFechaCarga(LocalDate fechaCarga) { this.fechaCarga = fechaCarga; }

    public String getTipoArchivoOrigen() { return tipoArchivoOrigen; }
    public void setTipoArchivoOrigen(String tipoArchivoOrigen) { this.tipoArchivoOrigen = tipoArchivoOrigen; }

    /**
     * Indica si esta factura tiene deuda (mora o saldo pendiente).
     */
    public boolean tieneMora() {
        return (valorMora != null && valorMora.compareTo(BigDecimal.ZERO) > 0)
                || (saldoMora != null && saldoMora.compareTo(BigDecimal.ZERO) > 0)
                || "VENCIDO".equals(estadoPago);
    }
}
