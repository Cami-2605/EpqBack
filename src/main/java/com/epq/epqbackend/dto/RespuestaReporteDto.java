package com.epq.epqbackend.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class RespuestaReporteDto {
    private List<Map<String, Object>> datos;
    private ResumenDto resumen;
    private String mensaje;
    private Integer totalRegistros;
    private byte[] archivo;
    private String nombreArchivo;
    private String tipoArchivo;

    public static class ResumenDto {
        private BigDecimal totalGeneral;
        private BigDecimal totalMora;
        private Integer totalFacturas;
        private Integer totalMorosos;
        private Map<String, BigDecimal> totalPorMunicipio;
        private Map<Integer, BigDecimal> totalPorEstrato;
        private Map<String, BigDecimal> totalPorMes;
        private List<Map<String, Object>> detallePorVivienda;

        public BigDecimal getTotalGeneral() {
            return totalGeneral;
        }

        public void setTotalGeneral(BigDecimal totalGeneral) {
            this.totalGeneral = totalGeneral;
        }

        public BigDecimal getTotalMora() {
            return totalMora;
        }

        public void setTotalMora(BigDecimal totalMora) {
            this.totalMora = totalMora;
        }

        public Integer getTotalFacturas() {
            return totalFacturas;
        }

        public void setTotalFacturas(Integer totalFacturas) {
            this.totalFacturas = totalFacturas;
        }

        public Integer getTotalMorosos() {
            return totalMorosos;
        }

        public void setTotalMorosos(Integer totalMorosos) {
            this.totalMorosos = totalMorosos;
        }

        public Map<String, BigDecimal> getTotalPorMunicipio() {
            return totalPorMunicipio;
        }

        public void setTotalPorMunicipio(Map<String, BigDecimal> totalPorMunicipio) {
            this.totalPorMunicipio = totalPorMunicipio;
        }

        public Map<Integer, BigDecimal> getTotalPorEstrato() {
            return totalPorEstrato;
        }

        public void setTotalPorEstrato(Map<Integer, BigDecimal> totalPorEstrato) {
            this.totalPorEstrato = totalPorEstrato;
        }

        public Map<String, BigDecimal> getTotalPorMes() {
            return totalPorMes;
        }

        public void setTotalPorMes(Map<String, BigDecimal> totalPorMes) {
            this.totalPorMes = totalPorMes;
        }

        public List<Map<String, Object>> getDetallePorVivienda() {
            return detallePorVivienda;
        }

        public void setDetallePorVivienda(List<Map<String, Object>> detallePorVivienda) {
            this.detallePorVivienda = detallePorVivienda;
        }
    }

    public List<Map<String, Object>> getDatos() {
        return datos;
    }

    public void setDatos(List<Map<String, Object>> datos) {
        this.datos = datos;
    }

    public ResumenDto getResumen() {
        return resumen;
    }

    public void setResumen(ResumenDto resumen) {
        this.resumen = resumen;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Integer getTotalRegistros() {
        return totalRegistros;
    }

    public void setTotalRegistros(Integer totalRegistros) {
        this.totalRegistros = totalRegistros;
    }

    public byte[] getArchivo() {
        return archivo;
    }

    public void setArchivo(byte[] archivo) {
        this.archivo = archivo;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getTipoArchivo() {
        return tipoArchivo;
    }

    public void setTipoArchivo(String tipoArchivo) {
        this.tipoArchivo = tipoArchivo;
    }
}