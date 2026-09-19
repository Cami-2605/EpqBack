package com.epq.epqbackend.exception;

import com.epq.epqbackend.dto.ApiErrorDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Manejo centralizado de errores.
 * Todos los errores devuelven ApiErrorDto con la misma estructura JSON.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Errores del dominio EPQ (nuestra excepción personalizada).
     * Incluyen código semántico y detalle para el frontend.
     */
    @ExceptionHandler(EpqException.class)
    public ResponseEntity<ApiErrorDto> handleEpqException(EpqException ex, HttpServletRequest request) {
        ApiErrorDto error = new ApiErrorDto(
                ex.getErrorCode().getCodigo(),
                ex.getMessage()
        );
        error.setDetalle(ex.getDetalle());
        error.setPath(request.getRequestURI());
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    /**
     * Errores de validación de Bean Validation (@Valid, @NotNull, etc.).
     * Devuelve la lista de campos con error.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorDto> handleValidacion(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiErrorDto.CampoErrorDto> erroresCampos = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map((FieldError fe) -> new ApiErrorDto.CampoErrorDto(fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());

        ApiErrorDto error = new ApiErrorDto(
                ErrorCode.DATOS_INCOMPLETOS.getCodigo(),
                "Los datos enviados contienen errores de validación"
        );
        error.setErroresCampos(erroresCampos);
        error.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(error);
    }

    /**
     * Archivo demasiado grande (supera max-file-size configurado en application.yaml).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorDto> handleArchivoGrande(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        ApiErrorDto error = new ApiErrorDto(
                ErrorCode.ARCHIVO_MUY_GRANDE.getCodigo(),
                "El archivo supera el tamaño máximo permitido (50 MB)",
                ex.getMessage()
        );
        error.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(error);
    }

    /**
     * Error general en la carga de multipart (archivo corrupto en tránsito, etc.).
     */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiErrorDto> handleMultipart(MultipartException ex, HttpServletRequest request) {
        ApiErrorDto error = new ApiErrorDto(
                ErrorCode.ARCHIVO_CORRUPTO.getCodigo(),
                "Error al recibir el archivo. Verifique que el archivo no esté corrupto",
                ex.getMessage()
        );
        error.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Parámetro requerido ausente en la petición.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorDto> handleParametroFaltante(MissingServletRequestParameterException ex, HttpServletRequest request) {
        ApiErrorDto error = new ApiErrorDto(
                ErrorCode.DATOS_INCOMPLETOS.getCodigo(),
                "Parámetro requerido ausente: '" + ex.getParameterName() + "'"
        );
        error.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * RuntimeException genérica (compatibilidad con código existente que lanza RuntimeException).
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiErrorDto> handleRuntime(RuntimeException ex, HttpServletRequest request) {
        ApiErrorDto error = new ApiErrorDto(
                ErrorCode.ERROR_INTERNO.getCodigo(),
                ex.getMessage() != null ? ex.getMessage() : "Error inesperado en el servidor"
        );
        error.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Cualquier otra excepción no contemplada.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorDto> handleGeneral(Exception ex, HttpServletRequest request) {
        ApiErrorDto error = new ApiErrorDto(
                ErrorCode.ERROR_INTERNO.getCodigo(),
                "Error interno del servidor. Por favor, intente nuevamente",
                ex.getMessage()
        );
        error.setPath(request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
