package com.epq.epqbackend.service;

import com.epq.epqbackend.exception.EpqException;
import com.epq.epqbackend.exception.ErrorCode;
import com.epq.epqbackend.model.ArchivoExcel;
import com.epq.epqbackend.model.Factura;
import com.epq.epqbackend.repository.ArchivoExcelRepository;
import com.epq.epqbackend.repository.FacturaRepository;
import com.epq.epqbackend.util.ExcelParserUtil;
import com.epq.epqbackend.util.PdfExtractorUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Orquesta la carga y procesamiento de múltiples archivos (Excel y PDF).
 *
 * Responsabilidades:
 * - Identificar el tipo de cada archivo por su extensión.
 * - Delegar el parseo a ExcelParserUtil o PdfExtractorUtil según corresponda.
 * - Consolidar los resultados de todos los archivos.
 * - Persistir las facturas extraídas y el registro de metadatos del archivo.
 * - Devolver un ResultadoCarga con el resumen del proceso.
 *
 * Política de errores parciales:
 * - Si un archivo completo falla (corrupto, formato inválido), se registra el error
 *   y se continúa con los siguientes archivos.
 * - Si un archivo tiene errores en filas individuales, las filas válidas se guardan
 *   y los errores se incluyen en el resultado.
 */
@Service
public class ArchivoProcessorService {

    @Autowired
    private FacturaRepository facturaRepository;

    @Autowired
    private ArchivoExcelRepository archivoExcelRepository;

    private static final int BATCH_SIZE = 1000;

    // ── DTO de resultado ───────────────────────────────────────────────────────

    public static class ResultadoCarga {

        public static class ResultadoArchivo {
            private final String nombreArchivo;
            private final String tipoArchivo;
            private final int registrosProcesados;
            private final int registrosFallidos;
            private final String estado;           // "PROCESADO", "PARCIAL", "ERROR"
            private final List<String> errores;

            public ResultadoArchivo(String nombreArchivo, String tipoArchivo,
                                    int procesados, int fallidos,
                                    String estado, List<String> errores) {
                this.nombreArchivo = nombreArchivo;
                this.tipoArchivo = tipoArchivo;
                this.registrosProcesados = procesados;
                this.registrosFallidos = fallidos;
                this.estado = estado;
                this.errores = errores;
            }

            public String getNombreArchivo()     { return nombreArchivo; }
            public String getTipoArchivo()       { return tipoArchivo; }
            public int getRegistrosProcesados()  { return registrosProcesados; }
            public int getRegistrosFallidos()    { return registrosFallidos; }
            public String getEstado()            { return estado; }
            public List<String> getErrores()     { return errores; }
        }

        private final List<ResultadoArchivo> archivos;
        private final int totalRegistrosProcesados;
        private final int totalRegistrosFallidos;
        private final int archivosConError;
        private final String mensaje;

        public ResultadoCarga(List<ResultadoArchivo> archivos) {
            this.archivos = archivos;
            this.totalRegistrosProcesados = archivos.stream()
                    .mapToInt(ResultadoArchivo::getRegistrosProcesados).sum();
            this.totalRegistrosFallidos = archivos.stream()
                    .mapToInt(ResultadoArchivo::getRegistrosFallidos).sum();
            this.archivosConError = (int) archivos.stream()
                    .filter(a -> "ERROR".equals(a.getEstado())).count();
            this.mensaje = construirMensaje();
        }

        private String construirMensaje() {
            int procesados = archivos.stream()
                    .filter(a -> !"ERROR".equals(a.getEstado())).mapToInt(a -> 1).sum();
            return procesados + " archivo(s) procesados correctamente, " +
                   archivosConError + " con error(es). " +
                   totalRegistrosProcesados + " registros guardados.";
        }

        public List<ResultadoArchivo> getArchivos()       { return archivos; }
        public int getTotalRegistrosProcesados()          { return totalRegistrosProcesados; }
        public int getTotalRegistrosFallidos()            { return totalRegistrosFallidos; }
        public int getArchivosConError()                  { return archivosConError; }
        public String getMensaje()                        { return mensaje; }
    }

    // ── Método principal: múltiples archivos ───────────────────────────────────

    /**
     * Procesa una lista de archivos (Excel y/o PDF) y persiste los resultados.
     *
     * @param archivos   lista de archivos a procesar
     * @param tipoFuente "base" o "extra"
     * @return ResultadoCarga con el resumen de cada archivo
     */
    public ResultadoCarga procesarArchivos(List<MultipartFile> archivos, String tipoFuente) {
        if (archivos == null || archivos.isEmpty()) {
            throw new EpqException(ErrorCode.ARCHIVO_VACIO, "No se enviaron archivos para procesar");
        }

        List<ResultadoCarga.ResultadoArchivo> resultados = new ArrayList<>();

        for (MultipartFile archivo : archivos) {
            String nombreArchivo = archivo.getOriginalFilename();
            if (nombreArchivo == null || nombreArchivo.isBlank()) {
                resultados.add(new ResultadoCarga.ResultadoArchivo(
                        "sin_nombre", "DESCONOCIDO", 0, 1, "ERROR",
                        List.of("El archivo no tiene nombre")));
                continue;
            }

            ResultadoCarga.ResultadoArchivo resultado;
            try {
                if (esExcel(nombreArchivo)) {
                    resultado = procesarExcel(archivo, tipoFuente, nombreArchivo);
                } else if (esPdf(nombreArchivo)) {
                    resultado = procesarPdf(archivo, tipoFuente, nombreArchivo);
                } else {
                    resultado = new ResultadoCarga.ResultadoArchivo(
                            nombreArchivo, "DESCONOCIDO", 0, 1, "ERROR",
                            List.of("Formato no soportado. Use .xls, .xlsx o .pdf"));
                    registrarArchivoConError(nombreArchivo, tipoFuente, archivo.getSize(),
                            "Formato no soportado");
                }
            } catch (EpqException e) {
                resultado = new ResultadoCarga.ResultadoArchivo(
                        nombreArchivo, detectarTipo(nombreArchivo), 0, 1, "ERROR",
                        List.of(e.getDetalle() != null ? e.getDetalle() : e.getMessage()));
                registrarArchivoConError(nombreArchivo, tipoFuente, archivo.getSize(), e.getMessage());
            } catch (Exception e) {
                resultado = new ResultadoCarga.ResultadoArchivo(
                        nombreArchivo, detectarTipo(nombreArchivo), 0, 1, "ERROR",
                        List.of("Error inesperado: " + e.getMessage()));
                registrarArchivoConError(nombreArchivo, tipoFuente, archivo.getSize(), e.getMessage());
            }
            resultados.add(resultado);
        }

        // Si todos los archivos fallaron, lanzar excepción para que el frontend lo sepa
        boolean todosFallaron = resultados.stream().allMatch(r -> "ERROR".equals(r.getEstado()));
        if (todosFallaron && !resultados.isEmpty()) {
            List<String> mensajesError = resultados.stream()
                    .flatMap(r -> r.getErrores().stream())
                    .toList();
            throw new EpqException(ErrorCode.EXCEL_SIN_DATOS,
                    "Ningún archivo pudo ser procesado. Verifique los archivos enviados",
                    String.join("; ", mensajesError));
        }

        return new ResultadoCarga(resultados);
    }

    // ── Procesamiento Excel ────────────────────────────────────────────────────

    @Transactional
    protected ResultadoCarga.ResultadoArchivo procesarExcel(MultipartFile archivo,
                                                            String tipoFuente,
                                                            String nombreArchivo) {
        ExcelParserUtil.ResultadoParseo resultado = ExcelParserUtil.parsear(
                archivo, tipoFuente, nombreArchivo);

        if (!resultado.tieneFacturas()) {
            registrarArchivo(nombreArchivo, tipoFuente, archivo.getSize(),
                    0, resultado.getErroresFila().size(),
                    "ERROR", resultado.getErroresFila());
            throw new EpqException(ErrorCode.EXCEL_SIN_DATOS,
                    "No se encontraron datos válidos en: " + nombreArchivo);
        }

        // Eliminar registros previos del mismo archivo
        try {
            facturaRepository.deleteByNombreArchivoOrigenAndTipoFuente(nombreArchivo, tipoFuente);
        } catch (Exception e) {
            // No bloquear si falla la limpieza; los datos nuevos se agregarán igualmente
        }

        // Guardar en lotes
        guardarEnLotes(resultado.getFacturas());

        // Determinar estado
        String estado = resultado.tieneErrores() ? "PARCIAL" : "PROCESADO";

        registrarArchivo(nombreArchivo, tipoFuente, archivo.getSize(),
                resultado.getFacturas().size(), resultado.getErroresFila().size(),
                estado, resultado.getErroresFila());

        return new ResultadoCarga.ResultadoArchivo(
                nombreArchivo, "EXCEL",
                resultado.getFacturas().size(),
                resultado.getErroresFila().size(),
                estado,
                resultado.getErroresFila());
    }

    // ── Procesamiento PDF ──────────────────────────────────────────────────────

    @Transactional
    protected ResultadoCarga.ResultadoArchivo procesarPdf(MultipartFile archivo,
                                                          String tipoFuente,
                                                          String nombreArchivo) {
        String texto = PdfExtractorUtil.extraerTexto(archivo);
        List<Map<String, String>> registrosExtraidos = PdfExtractorUtil.extraerRegistros(texto);

        if (registrosExtraidos.isEmpty()) {
            registrarArchivoConError(nombreArchivo, tipoFuente, archivo.getSize(),
                    "No se encontraron datos estructurados en el PDF");
            throw new EpqException(ErrorCode.PDF_SIN_DATOS_ESTRUCTURADOS,
                    "No se pudo extraer información estructurada del PDF: " + nombreArchivo +
                    ". Verifique que el PDF contenga texto seleccionable y datos de facturación.");
        }

        List<Factura> facturas = new ArrayList<>();
        List<String> errores = new ArrayList<>();

        for (int i = 0; i < registrosExtraidos.size(); i++) {
            Map<String, String> reg = registrosExtraidos.get(i);
            try {
                Factura f = convertirRegistroPdfAFactura(reg, tipoFuente, nombreArchivo, i + 1);
                facturas.add(f);
            } catch (Exception e) {
                errores.add("Registro " + (i + 1) + ": " + e.getMessage());
            }
        }

        if (facturas.isEmpty()) {
            registrarArchivoConError(nombreArchivo, tipoFuente, archivo.getSize(),
                    "Registros extraídos sin datos mínimos válidos");
            throw new EpqException(ErrorCode.PDF_SIN_DATOS_ESTRUCTURADOS,
                    "Los registros extraídos del PDF no contienen datos mínimos (municipio + valor)");
        }

        try {
            facturaRepository.deleteByNombreArchivoOrigenAndTipoFuente(nombreArchivo, tipoFuente);
        } catch (Exception ignored) {}

        guardarEnLotes(facturas);

        String estado = errores.isEmpty() ? "PROCESADO" : "PARCIAL";
        registrarArchivo(nombreArchivo, tipoFuente, archivo.getSize(),
                facturas.size(), errores.size(), estado, errores);

        return new ResultadoCarga.ResultadoArchivo(
                nombreArchivo, "PDF",
                facturas.size(), errores.size(), estado, errores);
    }

    // ── Conversión de mapa PDF → Factura ──────────────────────────────────────

    private Factura convertirRegistroPdfAFactura(Map<String, String> reg,
                                                  String tipoFuente,
                                                  String nombreArchivo,
                                                  int numRegistro) {
        String municipio = reg.get("municipio");
        if (municipio == null || municipio.isBlank()) {
            throw new EpqException(ErrorCode.PDF_MUNICIPIO_NO_DETECTADO,
                    "Municipio no detectado en registro " + numRegistro);
        }

        Factura f = new Factura();
        f.setMunicipio(municipio);

        // Fecha de factura
        String fechaStr = reg.get("fechaFactura");
        LocalDate fecha = fechaStr != null && !fechaStr.isBlank()
                ? intentarParsearFecha(fechaStr)
                : LocalDate.now();
        f.setFechaFactura(fecha);
        f.setFechaVencimiento(fecha.plusDays(30));
        f.setMesReferencia(fecha.getYear() + "-" + String.format("%02d", fecha.getMonthValue()));

        // Valores monetarios
        f.setValorFactura(parsearBigDecimalSeguro(reg.get("valorFactura"), BigDecimal.ZERO));
        f.setValorMora(parsearBigDecimalSeguro(reg.get("valorMora"), BigDecimal.ZERO));
        f.setSaldoMora(parsearBigDecimalSeguro(reg.get("saldoMora"), BigDecimal.ZERO));
        f.setTotalPagar(f.getValorFactura().add(f.getValorMora()));

        // Estrato
        String estratoStr = reg.get("estrato");
        if (estratoStr != null && !estratoStr.isBlank()) {
            try { f.setEstrato(Integer.parseInt(estratoStr.trim())); }
            catch (NumberFormatException ignored) {}
        }

        // Campos de texto
        f.setDireccionServicio(reg.get("direccion"));
        f.setNumeroContrato(reg.get("contrato"));

        // Estado de pago
        f.setEstadoPago(f.getFechaVencimiento().isBefore(LocalDate.now()) ? "VENCIDO" : "PENDIENTE");

        // Trazabilidad
        f.setTipoFuente(tipoFuente);
        f.setNombreArchivoOrigen(nombreArchivo);
        f.setFechaCarga(LocalDate.now());
        f.setTipoArchivoOrigen("PDF");

        return f;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void guardarEnLotes(List<Factura> facturas) {
        for (int i = 0; i < facturas.size(); i += BATCH_SIZE) {
            int fin = Math.min(i + BATCH_SIZE, facturas.size());
            facturaRepository.saveAll(facturas.subList(i, fin));
        }
    }

    private void registrarArchivo(String nombreArchivo, String tipoFuente, long peso,
                                   int procesados, int fallidos,
                                   String estado, List<String> errores) {
        try {
            archivoExcelRepository.deleteByNombreArchivo(nombreArchivo);

            ArchivoExcel ae = new ArchivoExcel();
            ae.setNombreArchivo(nombreArchivo);
            ae.setPeso(peso);
            ae.setFechaCarga(LocalDateTime.now());
            ae.setTipo(tipoFuente);
            ae.setRegistrosProcesados(procesados);
            ae.setRegistrosFallidos(fallidos);
            ae.setEstado(estado);
            if (errores != null && !errores.isEmpty()) {
                String resumen = String.join("; ", errores.stream().limit(5).toList());
                if (errores.size() > 5) resumen += " ... y " + (errores.size() - 5) + " más";
                ae.setErrores(resumen.length() > 2000 ? resumen.substring(0, 2000) : resumen);
            }
            archivoExcelRepository.save(ae);
        } catch (Exception e) {
            // No bloquear el flujo si falla el registro de metadatos
        }
    }

    private void registrarArchivoConError(String nombreArchivo, String tipoFuente,
                                           long peso, String mensajeError) {
        registrarArchivo(nombreArchivo, tipoFuente, peso, 0, 1, "ERROR",
                List.of(mensajeError != null ? mensajeError : "Error desconocido"));
    }

    private static boolean esExcel(String nombre) {
        String n = nombre.toLowerCase();
        return n.endsWith(".xls") || n.endsWith(".xlsx") || n.endsWith(".xlsm");
    }

    private static boolean esPdf(String nombre) {
        return nombre.toLowerCase().endsWith(".pdf");
    }

    private static String detectarTipo(String nombre) {
        if (esExcel(nombre)) return "EXCEL";
        if (esPdf(nombre))   return "PDF";
        return "DESCONOCIDO";
    }

    private static LocalDate intentarParsearFecha(String fechaStr) {
        try { return ExcelParserUtil.parsearFecha(fechaStr, 0); }
        catch (Exception e) { return LocalDate.now(); }
    }

    private static BigDecimal parsearBigDecimalSeguro(String valor, BigDecimal defecto) {
        if (valor == null || valor.isBlank()) return defecto;
        try { return ExcelParserUtil.parsearBigDecimal(valor, 0); }
        catch (Exception e) { return defecto; }
    }
}
