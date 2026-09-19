package com.epq.epqbackend.exception;

/**
 * Códigos de error estandarizados para toda la aplicación.
 * Permite al frontend identificar el tipo de error y mostrar mensajes apropiados.
 */
public enum ErrorCode {

    // Errores de archivo
    ARCHIVO_FORMATO_NO_SOPORTADO("ERR_001", "Formato de archivo no soportado"),
    ARCHIVO_VACIO("ERR_002", "El archivo está vacío"),
    ARCHIVO_CORRUPTO("ERR_003", "El archivo está corrupto o no se puede leer"),
    ARCHIVO_SIN_NOMBRE("ERR_004", "El archivo no tiene nombre válido"),
    ARCHIVO_MUY_GRANDE("ERR_005", "El archivo supera el tamaño máximo permitido"),

    // Errores de procesamiento Excel
    EXCEL_COLUMNAS_FALTANTES("ERR_101", "El archivo Excel no contiene todas las columnas requeridas"),
    EXCEL_SIN_DATOS("ERR_102", "El archivo Excel no contiene datos para procesar"),
    EXCEL_FILA_INVALIDA("ERR_103", "Una o más filas contienen datos inválidos"),
    EXCEL_FECHA_INVALIDA("ERR_104", "Formato de fecha no reconocido"),
    EXCEL_VALOR_INVALIDO("ERR_105", "Valor numérico inválido"),

    // Errores de procesamiento PDF
    PDF_NO_LEGIBLE("ERR_201", "No se pudo extraer texto del PDF"),
    PDF_SIN_DATOS_ESTRUCTURADOS("ERR_202", "El PDF no contiene información estructurada reconocible"),
    PDF_MUNICIPIO_NO_DETECTADO("ERR_203", "No se detectó el municipio en el PDF"),

    // Errores de validación de datos
    MUNICIPIO_INVALIDO("ERR_301", "Municipio no reconocido o no pertenece a los municipios del sistema"),
    ESTRATO_INVALIDO("ERR_302", "Estrato socioeconómico inválido (debe ser entre 1 y 6)"),
    FECHA_RANGO_INVALIDO("ERR_303", "El rango de fechas es inválido"),
    DATOS_INCOMPLETOS("ERR_304", "La información proporcionada está incompleta"),

    // Errores de reporte
    REPORTE_SIN_DATOS("ERR_401", "No hay datos que coincidan con los filtros seleccionados"),
    REPORTE_TIPO_INVALIDO("ERR_402", "Tipo de reporte no reconocido"),
    REPORTE_FORMATO_INVALIDO("ERR_403", "Formato de exportación no soportado"),
    REPORTE_ERROR_GENERACION("ERR_404", "Error al generar el archivo del reporte"),

    // Errores de autenticación / usuario
    USUARIO_NO_ENCONTRADO("ERR_501", "Usuario no encontrado"),
    CREDENCIALES_INVALIDAS("ERR_502", "Credenciales inválidas"),
    USUARIO_YA_EXISTE("ERR_503", "El nombre de usuario ya está en uso"),
    EMAIL_YA_REGISTRADO("ERR_504", "El correo electrónico ya está registrado"),

    // Errores generales
    ERROR_INTERNO("ERR_999", "Error interno del servidor");

    private final String codigo;
    private final String descripcion;

    ErrorCode(String codigo, String descripcion) {
        this.codigo = codigo;
        this.descripcion = descripcion;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
