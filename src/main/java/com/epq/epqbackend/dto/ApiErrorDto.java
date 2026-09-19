package com.epq.epqbackend.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Respuesta estándar para todos los errores de la API.
 * El frontend siempre recibe la misma estructura independientemente del tipo de error.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorDto {

    /** Código de error del enum ErrorCode, p.ej. "ERR_101" */
    private String codigo;

    /** Mensaje principal legible para el usuario */
    private String mensaje;

    /** Información técnica adicional (solo visible en dev/staging, nunca stacktrace) */
    private String detalle;

    /** Momento en que ocurrió el error */
    private LocalDateTime timestamp;

    /** Ruta HTTP que generó el error */
    private String path;

    /** Errores de validación de campos individuales (Bean Validation) */
    private List<CampoErrorDto> erroresCampos;

    /** Errores parciales al procesar múltiples archivos/filas */
    private List<String> erroresParciales;

    public ApiErrorDto() {
        this.timestamp = LocalDateTime.now();
    }

    public ApiErrorDto(String codigo, String mensaje) {
        this.codigo = codigo;
        this.mensaje = mensaje;
        this.timestamp = LocalDateTime.now();
    }

    public ApiErrorDto(String codigo, String mensaje, String detalle) {
        this.codigo = codigo;
        this.mensaje = mensaje;
        this.detalle = detalle;
        this.timestamp = LocalDateTime.now();
    }

    // ---- getters y setters ----

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getMensaje() { return mensaje; }
    public void setMensaje(String mensaje) { this.mensaje = mensaje; }

    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public List<CampoErrorDto> getErroresCampos() { return erroresCampos; }
    public void setErroresCampos(List<CampoErrorDto> erroresCampos) { this.erroresCampos = erroresCampos; }

    public List<String> getErroresParciales() { return erroresParciales; }
    public void setErroresParciales(List<String> erroresParciales) { this.erroresParciales = erroresParciales; }

    // ---- clase interna para errores de validación por campo ----

    public static class CampoErrorDto {
        private String campo;
        private String mensaje;

        public CampoErrorDto(String campo, String mensaje) {
            this.campo = campo;
            this.mensaje = mensaje;
        }

        public String getCampo() { return campo; }
        public void setCampo(String campo) { this.campo = campo; }

        public String getMensaje() { return mensaje; }
        public void setMensaje(String mensaje) { this.mensaje = mensaje; }
    }
}
