package com.epq.epqbackend.exception;

import org.springframework.http.HttpStatus;

/**
 * Excepción personalizada del sistema EPQ.
 * Lleva un ErrorCode para que el frontend pueda identificar el tipo de problema,
 * un HttpStatus apropiado y opcionalmente detalles adicionales.
 */
public class EpqException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;
    private final String detalle;

    public EpqException(ErrorCode errorCode) {
        super(errorCode.getDescripcion());
        this.errorCode = errorCode;
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.detalle = null;
    }

    public EpqException(ErrorCode errorCode, String detalle) {
        super(errorCode.getDescripcion());
        this.errorCode = errorCode;
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.detalle = detalle;
    }

    public EpqException(ErrorCode errorCode, HttpStatus httpStatus, String detalle) {
        super(errorCode.getDescripcion());
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.detalle = detalle;
    }

    /**
     * Constructor con mensaje personalizado y detalle separado.
     * El mensaje sobreescribe el texto del ErrorCode.
     */
    public EpqException(ErrorCode errorCode, String mensaje, String detalle) {
        super(mensaje);
        this.errorCode = errorCode;
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.detalle = detalle;
    }

    public EpqException(ErrorCode errorCode, String detalle, Throwable causa) {
        super(errorCode.getDescripcion(), causa);
        this.errorCode = errorCode;
        this.httpStatus = HttpStatus.BAD_REQUEST;
        this.detalle = detalle;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDetalle() {
        return detalle;
    }
}
