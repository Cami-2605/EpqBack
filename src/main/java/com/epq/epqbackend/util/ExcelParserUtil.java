package com.epq.epqbackend.util;

import com.epq.epqbackend.exception.EpqException;
import com.epq.epqbackend.exception.ErrorCode;
import com.epq.epqbackend.model.Factura;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Utilidad para parsear archivos Excel (.xls / .xlsx / .xlsm) y convertirlos en
 * objetos Factura listos para persistir.
 *
 * Separada de FacturaService para que pueda ser reutilizada por ArchivoProcessorService
 * al procesar múltiples archivos sin duplicar código.
 *
 * Política de errores:
 * - Columnas obligatorias faltantes → lanza EpqException inmediatamente.
 * - Errores en filas individuales → se acumulan en ResultadoParseo.erroresFila.
 *   El servicio que invoca decide si tolerar errores parciales o rechazar todo.
 */
public final class ExcelParserUtil {

    private ExcelParserUtil() {}

    // ── Formatos de fecha soportados ───────────────────────────────────────────
    private static final DateTimeFormatter FMT_ISO      = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DDMMYY   = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter FMT_DDMMYYYY2 = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // ── Resultado del parseo ───────────────────────────────────────────────────

    public static class ResultadoParseo {
        private final List<Factura> facturas;
        private final List<String> erroresFila;
        private final int totalFilasLeidas;

        public ResultadoParseo(List<Factura> facturas, List<String> erroresFila, int totalFilasLeidas) {
            this.facturas = facturas;
            this.erroresFila = erroresFila;
            this.totalFilasLeidas = totalFilasLeidas;
        }

        public List<Factura> getFacturas() { return facturas; }
        public List<String> getErroresFila() { return erroresFila; }
        public int getTotalFilasLeidas() { return totalFilasLeidas; }
        public boolean tieneErrores() { return !erroresFila.isEmpty(); }
        public boolean tieneFacturas() { return !facturas.isEmpty(); }
    }

    // ── Método principal ───────────────────────────────────────────────────────

    /**
     * Parsea el archivo Excel y devuelve un ResultadoParseo con las facturas
     * extraídas y los errores por fila encontrados.
     *
     * @param archivo        el archivo MultipartFile
     * @param tipoFuente     "base" o "extra"
     * @param nombreArchivo  nombre del archivo (para trazabilidad)
     * @return ResultadoParseo con facturas y lista de errores
     */
    public static ResultadoParseo parsear(MultipartFile archivo, String tipoFuente, String nombreArchivo) {
        validarArchivo(archivo, nombreArchivo);

        List<Factura> facturas = new ArrayList<>();
        List<String> erroresFila = new ArrayList<>();

        try (InputStream is = archivo.getInputStream();
             Workbook workbook = abrirWorkbook(is, nombreArchivo)) {

            Sheet sheet = workbook.getSheetAt(0);

            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new EpqException(ErrorCode.EXCEL_SIN_DATOS,
                        "La primera hoja del archivo está vacía: " + nombreArchivo);
            }

            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                throw new EpqException(ErrorCode.EXCEL_COLUMNAS_FALTANTES,
                        "El archivo no tiene fila de encabezados: " + nombreArchivo);
            }

            // Mapear columnas normalizadas → índice
            Map<String, Integer> columnas = mapearColumnas(headerRow);

            // Verificar columnas obligatorias
            Integer colMunicipio = columnas.get("municipio");
            Integer colFecha = columnas.containsKey("fecharecaudo") ? columnas.get("fecharecaudo")
                             : columnas.get("fecha");
            Integer colValor  = columnas.get("valor");

            List<String> faltantes = new ArrayList<>();
            if (colMunicipio == null) faltantes.add("MUNICIPIO");
            if (colFecha     == null) faltantes.add("FECHA / FECHARECAUDO");
            if (colValor     == null) faltantes.add("VALOR");

            if (!faltantes.isEmpty()) {
                throw new EpqException(ErrorCode.EXCEL_COLUMNAS_FALTANTES,
                        "Columnas obligatorias no encontradas en '" + nombreArchivo + "': "
                        + String.join(", ", faltantes)
                        + ". Columnas detectadas: " + columnas.keySet());
            }

            // Columnas opcionales
            Integer colEstrato    = columnas.get("estrato");
            Integer colServicio   = columnas.get("servicio");
            Integer colDireccion  = columnas.get("direccion");
            Integer colBarrio     = columnas.get("barrio");
            Integer colContrato   = columnas.get("contrato");
            Integer colMora       = columnas.get("mora");
            Integer colSaldoMora  = columnas.containsKey("saldomora") ? columnas.get("saldomora")
                                  : columnas.get("saldo");
            Integer colVencimiento = columnas.get("vencimiento");

            int totalFilas = sheet.getLastRowNum();

            for (int i = 1; i <= totalFilas; i++) {
                Row row = sheet.getRow(i);
                if (row == null || esFilaVacia(row)) continue;

                try {
                    Factura f = parsearFila(row, i + 1,
                            colMunicipio, colFecha, colValor,
                            colEstrato, colServicio, colDireccion, colBarrio,
                            colContrato, colMora, colSaldoMora, colVencimiento,
                            tipoFuente, nombreArchivo);
                    facturas.add(f);

                } catch (EpqException e) {
                    erroresFila.add("Fila " + (i + 1) + ": " + e.getDetalle());
                } catch (Exception e) {
                    erroresFila.add("Fila " + (i + 1) + ": " + e.getMessage());
                }
            }

            return new ResultadoParseo(facturas, erroresFila, totalFilas);

        } catch (EpqException e) {
            throw e;
        } catch (Exception e) {
            throw new EpqException(ErrorCode.ARCHIVO_CORRUPTO,
                    "Error leyendo el archivo Excel '" + nombreArchivo + "': " + e.getMessage(), e);
        }
    }

    // ── Parseo de una fila ─────────────────────────────────────────────────────

    private static Factura parsearFila(Row row, int numFila,
            Integer colMunicipio, Integer colFecha, Integer colValor,
            Integer colEstrato, Integer colServicio, Integer colDireccion, Integer colBarrio,
            Integer colContrato, Integer colMora, Integer colSaldoMora, Integer colVencimiento,
            String tipoFuente, String nombreArchivo) {

        Factura f = new Factura();

        // Municipio (obligatorio)
        String municipioRaw = obtenerCelda(row, colMunicipio);
        if (municipioRaw == null || municipioRaw.isBlank()) {
            throw new EpqException(ErrorCode.DATOS_INCOMPLETOS, "Municipio vacío en fila " + numFila);
        }
        String municipioNorm = MunicipioConstantes.normalizar(municipioRaw);
        // Si no se reconoce, se guarda igual en mayúsculas (tolerante con municipios desconocidos)
        f.setMunicipio(municipioNorm != null ? municipioNorm : municipioRaw.trim().toUpperCase());

        // Fecha (obligatoria)
        String fechaStr = obtenerCelda(row, colFecha);
        if (fechaStr == null || fechaStr.isBlank()) {
            throw new EpqException(ErrorCode.EXCEL_FECHA_INVALIDA, "Fecha vacía en fila " + numFila);
        }
        LocalDate fechaFactura = parsearFecha(fechaStr.trim(), numFila);
        f.setFechaFactura(fechaFactura);
        f.setMesReferencia(fechaFactura.getYear() + "-" + String.format("%02d", fechaFactura.getMonthValue()));

        // Valor factura (obligatorio)
        String valorStr = obtenerCelda(row, colValor);
        if (valorStr == null || valorStr.isBlank()) {
            throw new EpqException(ErrorCode.EXCEL_VALOR_INVALIDO, "Valor vacío en fila " + numFila);
        }
        BigDecimal valorFactura = parsearBigDecimal(valorStr.trim(), numFila);
        f.setValorFactura(valorFactura);

        // Fecha vencimiento (opcional, default: fecha + 30 días)
        if (colVencimiento != null) {
            String vencStr = obtenerCelda(row, colVencimiento);
            if (vencStr != null && !vencStr.isBlank()) {
                try {
                    f.setFechaVencimiento(parsearFecha(vencStr.trim(), numFila));
                } catch (Exception e) {
                    f.setFechaVencimiento(fechaFactura.plusDays(30));
                }
            } else {
                f.setFechaVencimiento(fechaFactura.plusDays(30));
            }
        } else {
            f.setFechaVencimiento(fechaFactura.plusDays(30));
        }

        // Valor mora (opcional)
        if (colMora != null) {
            String moraStr = obtenerCelda(row, colMora);
            if (moraStr != null && !moraStr.isBlank()) {
                try {
                    f.setValorMora(parsearBigDecimal(moraStr.trim(), numFila));
                } catch (Exception e) {
                    f.setValorMora(BigDecimal.ZERO);
                }
            } else {
                f.setValorMora(BigDecimal.ZERO);
            }
        } else {
            f.setValorMora(BigDecimal.ZERO);
        }

        // Saldo de mora (opcional)
        if (colSaldoMora != null) {
            String saldoStr = obtenerCelda(row, colSaldoMora);
            if (saldoStr != null && !saldoStr.isBlank()) {
                try {
                    f.setSaldoMora(parsearBigDecimal(saldoStr.trim(), numFila));
                } catch (Exception e) {
                    f.setSaldoMora(BigDecimal.ZERO);
                }
            } else {
                f.setSaldoMora(BigDecimal.ZERO);
            }
        } else {
            f.setSaldoMora(BigDecimal.ZERO);
        }

        // Total a pagar = valor factura + mora
        f.setTotalPagar(f.getValorFactura().add(f.getValorMora()));

        // Estrato (opcional)
        if (colEstrato != null) {
            String estratoStr = obtenerCelda(row, colEstrato);
            if (estratoStr != null && !estratoStr.isBlank()) {
                try {
                    int estrato = Integer.parseInt(estratoStr.trim());
                    if (estrato >= 1 && estrato <= 6) {
                        f.setEstrato(estrato);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        // Campos opcionales de texto
        if (colServicio  != null) f.setNombreServicio(obtenerCelda(row, colServicio));
        if (colDireccion != null) f.setDireccionServicio(obtenerCelda(row, colDireccion));
        if (colBarrio    != null) f.setBarrio(obtenerCelda(row, colBarrio));
        if (colContrato  != null) f.setNumeroContrato(obtenerCelda(row, colContrato));

        // Estado de pago
        f.setEstadoPago(f.getFechaVencimiento().isBefore(LocalDate.now()) ? "VENCIDO" : "PENDIENTE");

        // Trazabilidad
        f.setTipoFuente(tipoFuente);
        f.setNombreArchivoOrigen(nombreArchivo);
        f.setFechaCarga(LocalDate.now());
        f.setTipoArchivoOrigen("EXCEL");

        return f;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static Workbook abrirWorkbook(InputStream is, String nombreArchivo) throws Exception {
        String nombre = nombreArchivo.toLowerCase();
        if (nombre.endsWith(".xlsx") || nombre.endsWith(".xlsm")) {
            return new XSSFWorkbook(is);
        } else if (nombre.endsWith(".xls")) {
            return new HSSFWorkbook(is);
        } else {
            throw new EpqException(ErrorCode.ARCHIVO_FORMATO_NO_SOPORTADO,
                    "Formato no soportado: '" + nombreArchivo + "'. Use .xls o .xlsx");
        }
    }

    private static Map<String, Integer> mapearColumnas(Row headerRow) {
        Map<String, Integer> mapa = new LinkedHashMap<>();
        for (Cell cell : headerRow) {
            if (cell == null) continue;
            String nombreOriginal = obtenerValorCeldaComoTexto(cell);
            if (nombreOriginal != null && !nombreOriginal.isBlank()) {
                String normalizado = normalizarNombreColumna(nombreOriginal);
                mapa.put(normalizado, cell.getColumnIndex());
            }
        }
        return mapa;
    }

    /**
     * Normaliza el nombre de una columna: minúsculas, sin tildes, sin espacios ni guiones.
     */
    public static String normalizarNombreColumna(String nombre) {
        if (nombre == null) return "";
        return MunicipioConstantes.eliminarTildes(nombre)
                .toLowerCase()
                .replaceAll("[\\s\\-_/]+", "");
    }

    private static String obtenerCelda(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        return obtenerValorCeldaComoTexto(cell);
    }

    private static String obtenerValorCeldaComoTexto(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double num = cell.getNumericCellValue();
                // Evita notación científica para números enteros
                if (num == (long) num) return String.valueOf((long) num);
                return String.valueOf(num);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try { return String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) { return cell.getStringCellValue(); }
            default:
                return null;
        }
    }

    private static boolean esFilaVacia(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) return false;
        }
        return true;
    }

    // ── Parseo de fecha ────────────────────────────────────────────────────────

    public static LocalDate parsearFecha(String fechaStr, int numFila) {
        String s = fechaStr.trim();
        for (DateTimeFormatter fmt : List.of(FMT_ISO, FMT_DDMMYYYY, FMT_DDMMYYYY2)) {
            try { return LocalDate.parse(s, fmt); } catch (DateTimeParseException ignored) {}
        }
        // dd/MM/yy — ajustar año
        try {
            LocalDate d = LocalDate.parse(s, FMT_DDMMYY);
            if (d.getYear() < 100) d = d.plusYears(2000);
            return d;
        } catch (DateTimeParseException ignored) {}

        throw new EpqException(ErrorCode.EXCEL_FECHA_INVALIDA,
                "No se reconoce el formato de fecha '" + fechaStr + "' en fila " + numFila
                + ". Formatos soportados: yyyy-MM-dd, dd/MM/yyyy, dd-MM-yyyy, dd/MM/yy");
    }

    // ── Parseo de valor monetario ──────────────────────────────────────────────

    public static BigDecimal parsearBigDecimal(String valorStr, int numFila) {
        String s = valorStr.trim()
                .replace("$", "").replace("COP", "").replace("USD", "").trim();

        // Formato con ambos separadores: el punto es miles, la coma es decimal
        if (s.contains(",") && s.contains(".")) {
            s = s.replace(".", "").replace(",", ".");
        } else if (s.contains(",") && !s.contains(".")) {
            s = s.replace(",", ".");
        } else if (s.contains(".") && s.indexOf(".") < s.length() - 3) {
            // Punto como separador de miles (ej: 1.234.567)
            s = s.replace(".", "");
        }

        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new EpqException(ErrorCode.EXCEL_VALOR_INVALIDO,
                    "Valor numérico inválido '" + valorStr + "' en fila " + numFila);
        }
    }

    // ── Validación del archivo ─────────────────────────────────────────────────

    private static void validarArchivo(MultipartFile archivo, String nombreArchivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new EpqException(ErrorCode.ARCHIVO_VACIO,
                    "El archivo está vacío: " + nombreArchivo);
        }
        if (nombreArchivo == null || nombreArchivo.isBlank()) {
            throw new EpqException(ErrorCode.ARCHIVO_SIN_NOMBRE,
                    "El archivo no tiene nombre válido");
        }
        String nombre = nombreArchivo.toLowerCase();
        if (!nombre.endsWith(".xls") && !nombre.endsWith(".xlsx") && !nombre.endsWith(".xlsm")) {
            throw new EpqException(ErrorCode.ARCHIVO_FORMATO_NO_SOPORTADO,
                    "Formato no soportado: '" + nombreArchivo + "'. Use .xls, .xlsx o .xlsm");
        }
    }
}
