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
 * Utilidad para parsear archivos Excel (.xls / .xlsx / .xlsm).
 *
 * Soporta dos estructuras de archivo conocidas:
 *
 * ── ARCHIVO TIPO 1 (Cartera / Morosos) ──────────────────────────────────────
 * Columnas: CODIGO | NOMBRE | DIRECCION | TELEFONO | EMAIL | MUNICIPIO |
 *           MESES MORA | SERVICIO | ESTRATO | VALOR MES | VALOR ATRASO |
 *           VALOR TOTAL | VALOR PAGADO | FECHA PAGO | SALDO | NUMERO MEDIDOR
 *
 * Mapeo:
 *   municipio       ← MUNICIPIO
 *   fechaFactura    ← FECHA PAGO  (o fecha de corte si no hay fecha pago)
 *   valorFactura    ← VALOR MES
 *   valorMora       ← VALOR ATRASO
 *   saldoMora       ← SALDO
 *   totalPagar      ← VALOR TOTAL
 *   estrato         ← ESTRATO
 *   nombreServicio  ← SERVICIO
 *   direccionServicio ← DIRECCION
 *   mesesMora       ← MESES MORA
 *   numeroMedidor   ← NUMERO MEDIDOR
 *   codigoCliente   ← CODIGO
 *   nombreCliente   ← NOMBRE
 *
 * ── ARCHIVO TIPO 2 (Recaudos / Pagos) ───────────────────────────────────────
 * Columnas: CODIGO | NOMBRE | DIRECCION | MUNICIPIO | FECHA RECAUDO |
 *           TIPO RECAUDO | DOCUMENTO | PERIODO | TRANSACCION | VALOR |
 *           PAGADO MES | PAGADO ATRASO | MESES DE MORA | FILTRO | FECHA IMPRESION
 *
 * Mapeo:
 *   municipio       ← MUNICIPIO
 *   fechaFactura    ← FECHA RECAUDO
 *   mesReferencia   ← PERIODO  (si tiene el formato mm/yyyy o similar)
 *   valorFactura    ← PAGADO MES  (o VALOR si no existe)
 *   valorMora       ← PAGADO ATRASO
 *   totalPagar      ← VALOR
 *   mesesMora       ← MESES DE MORA
 *   nombreServicio  ← TIPO RECAUDO
 *   numeroContrato  ← DOCUMENTO
 *   direccionServicio ← DIRECCION
 *   codigoCliente   ← CODIGO
 *   nombreCliente   ← NOMBRE
 */
public final class ExcelParserUtil {

    private ExcelParserUtil() {}

    // ── Formatos de fecha soportados ───────────────────────────────────────────
    private static final DateTimeFormatter FMT_ISO       = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FMT_DDMMYYYY  = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_DDMMYY    = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final DateTimeFormatter FMT_DDMMYYYY2 = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter FMT_MMYYYY    = DateTimeFormatter.ofPattern("MM/yyyy");
    private static final DateTimeFormatter FMT_YYYYMM    = DateTimeFormatter.ofPattern("yyyy-MM");

    // ── Aliases de columnas ────────────────────────────────────────────────────
    // Normalizado (sin tildes, minúsculas, sin espacios) → campo interno

    /** Columnas que se interpretan como MUNICIPIO */
    private static final Set<String> COL_MUNICIPIO = Set.of(
            "municipio"
    );

    /** Columnas que se interpretan como FECHA DE FACTURA / RECAUDO */
    private static final Set<String> COL_FECHA = Set.of(
            "fecharecaudo", "fecha", "fechapago", "fechafactura",
            "fechaemision", "fechacorte"
    );

    /**
     * Columnas que se interpretan como VALOR DE FACTURA (cargo del mes).
     * Se recorre en orden de prioridad: la primera que exista en el archivo gana.
     */
    private static final List<String> COL_VALOR_FACTURA_PRIORIDAD = List.of(
            "valormes",    // Archivo 1: VALOR MES
            "pagadomes",   // Archivo 2: PAGADO MES
            "valorfactura",
            "valor"        // último recurso
    );

    /** Columnas que se interpretan como VALOR MORA */
    private static final List<String> COL_MORA_PRIORIDAD = List.of(
            "valoratraso",  // Archivo 1: VALOR ATRASO
            "pagadoatraso", // Archivo 2: PAGADO ATRASO
            "mora",
            "valormora"
    );

    /** Columnas que se interpretan como SALDO DE MORA acumulado */
    private static final List<String> COL_SALDO_PRIORIDAD = List.of(
            "saldo",
            "saldomora",
            "saldopendiente"
    );

    /** Columnas que se interpretan como VALOR TOTAL A PAGAR */
    private static final List<String> COL_TOTAL_PRIORIDAD = List.of(
            "valortotal",  // Archivo 1: VALOR TOTAL
            "valor",       // Archivo 2: VALOR (cuando no hay valormes)
            "totalpagar",
            "total"
    );

    /** Columnas que se interpretan como ESTRATO */
    private static final Set<String> COL_ESTRATO = Set.of(
            "estrato", "estratosocioeconomico"
    );

    /** Columnas que se interpretan como SERVICIO */
    private static final Set<String> COL_SERVICIO = Set.of(
            "servicio", "tiporecaudo", "tipoderecaudo", "tiposervicio",
            "nombreservicio"
    );

    /** Columnas que se interpretan como DIRECCIÓN */
    private static final Set<String> COL_DIRECCION = Set.of(
            "direccion", "direccionservicio", "dir"
    );

    /** Columnas que se interpretan como MESES EN MORA */
    private static final Set<String> COL_MESES_MORA = Set.of(
            "mesesmora",    // Archivo 1: MESES MORA
            "mesesdemora",  // Archivo 2: MESES DE MORA
            "mesesdeatraso"
    );

    /** Columnas que se interpretan como NÚMERO DE MEDIDOR */
    private static final Set<String> COL_MEDIDOR = Set.of(
            "numeromedidor", "medidor", "nromedidor"
    );

    /** Columnas que se interpretan como NÚMERO DE CONTRATO / DOCUMENTO */
    private static final Set<String> COL_CONTRATO = Set.of(
            "contrato", "documento", "numerodocumento", "nrocontrato",
            "numerocontrato", "transaccion"
    );

    /** Columnas que se interpretan como PERÍODO */
    private static final Set<String> COL_PERIODO = Set.of(
            "periodo", "mesreferencia", "mes"
    );

    /** Columnas que se interpretan como CÓDIGO DE CLIENTE */
    private static final Set<String> COL_CODIGO = Set.of(
            "codigo", "codigocliente", "codigousuario", "id"
    );

    /** Columnas que se interpretan como NOMBRE DE CLIENTE */
    private static final Set<String> COL_NOMBRE = Set.of(
            "nombre", "nombrecliente", "nombrecompleto", "usuario"
    );

    /** Columnas que se interpretan como BARRIO */
    private static final Set<String> COL_BARRIO = Set.of(
            "barrio", "sector", "zona"
    );

    // ── Clase resultado ────────────────────────────────────────────────────────

    public static class ResultadoParseo {
        private final List<Factura> facturas;
        private final List<String> erroresFila;
        private final int totalFilasLeidas;

        public ResultadoParseo(List<Factura> facturas, List<String> erroresFila, int totalFilasLeidas) {
            this.facturas = facturas;
            this.erroresFila = erroresFila;
            this.totalFilasLeidas = totalFilasLeidas;
        }

        public List<Factura> getFacturas()      { return facturas; }
        public List<String> getErroresFila()    { return erroresFila; }
        public int getTotalFilasLeidas()         { return totalFilasLeidas; }
        public boolean tieneErrores()            { return !erroresFila.isEmpty(); }
        public boolean tieneFacturas()           { return !facturas.isEmpty(); }
    }

    // ── Método principal ───────────────────────────────────────────────────────

    public static ResultadoParseo parsear(MultipartFile archivo,
                                          String tipoFuente,
                                          String nombreArchivo) {
        validarArchivo(archivo, nombreArchivo);

        List<Factura> facturas  = new ArrayList<>();
        List<String> errores    = new ArrayList<>();

        try (InputStream is = archivo.getInputStream();
             Workbook workbook = abrirWorkbook(is, nombreArchivo)) {

            Sheet sheet = workbook.getSheetAt(0);

            if (sheet == null || sheet.getPhysicalNumberOfRows() == 0) {
                throw new EpqException(ErrorCode.EXCEL_SIN_DATOS,
                        "La primera hoja del archivo está vacía: " + nombreArchivo);
            }

            // Buscar la fila de encabezados (puede no ser la fila 0 si hay títulos arriba)
            int indiceHeader = encontrarFilaEncabezado(sheet);
            Row headerRow = sheet.getRow(indiceHeader);
            if (headerRow == null) {
                throw new EpqException(ErrorCode.EXCEL_COLUMNAS_FALTANTES,
                        "No se encontró fila de encabezados en: " + nombreArchivo);
            }

            // Mapear nombre normalizado → índice de columna
            Map<String, Integer> columnas = mapearColumnas(headerRow);

            System.out.println("=== Columnas detectadas en '" + nombreArchivo + "' ===");
            columnas.forEach((k, v) -> System.out.println("  [" + v + "] " + k));

            // Resolver índices con alias
            ColIndices idx = resolverIndices(columnas);

            // MUNICIPIO es obligatorio
            if (idx.municipio == null) {
                throw new EpqException(ErrorCode.EXCEL_COLUMNAS_FALTANTES,
                        "Columna MUNICIPIO no encontrada en '" + nombreArchivo + "'. " +
                        "Columnas detectadas: " + columnas.keySet());
            }

            // FECHA es obligatoria
            if (idx.fecha == null) {
                throw new EpqException(ErrorCode.EXCEL_COLUMNAS_FALTANTES,
                        "Columna de FECHA no encontrada en '" + nombreArchivo + "'. " +
                        "Columnas esperadas: FECHA RECAUDO, FECHA PAGO, FECHA. " +
                        "Columnas detectadas: " + columnas.keySet());
            }

            // Al menos un campo de valor es obligatorio
            if (idx.valorFactura == null && idx.total == null) {
                throw new EpqException(ErrorCode.EXCEL_COLUMNAS_FALTANTES,
                        "No se encontró columna de VALOR en '" + nombreArchivo + "'. " +
                        "Columnas esperadas: VALOR MES, PAGADO MES, VALOR, VALOR TOTAL. " +
                        "Columnas detectadas: " + columnas.keySet());
            }

            int totalFilas = sheet.getLastRowNum();

            for (int i = indiceHeader + 1; i <= totalFilas; i++) {
                Row row = sheet.getRow(i);
                if (row == null || esFilaVacia(row)) continue;

                try {
                    Factura f = parsearFila(row, i + 1, idx, tipoFuente, nombreArchivo);
                    facturas.add(f);
                } catch (EpqException e) {
                    errores.add("Fila " + (i + 1) + ": " + e.getDetalle());
                } catch (Exception e) {
                    errores.add("Fila " + (i + 1) + ": " + e.getMessage());
                }
            }

            System.out.println("'" + nombreArchivo + "': " + facturas.size()
                    + " facturas, " + errores.size() + " errores");

            return new ResultadoParseo(facturas, errores, totalFilas - indiceHeader);

        } catch (EpqException e) {
            throw e;
        } catch (Exception e) {
            throw new EpqException(ErrorCode.ARCHIVO_CORRUPTO,
                    "Error leyendo '" + nombreArchivo + "': " + e.getMessage(), e);
        }
    }

    // ── Contenedor de índices resueltos ────────────────────────────────────────

    private static class ColIndices {
        Integer municipio, fecha, valorFactura, mora, saldo, total;
        Integer estrato, servicio, direccion, barrio, contrato;
        Integer mesesMora, medidor, codigo, nombre, periodo;
    }

    /**
     * Recorre los alias en orden de prioridad y devuelve el primero que exista
     * en el archivo, o null si ninguno existe.
     */
    private static Integer primero(Map<String, Integer> col, List<String> candidatos) {
        for (String c : candidatos) {
            if (col.containsKey(c)) return col.get(c);
        }
        return null;
    }

    private static Integer primeroDeSet(Map<String, Integer> col, Set<String> candidatos) {
        for (String c : candidatos) {
            if (col.containsKey(c)) return col.get(c);
        }
        return null;
    }

    private static ColIndices resolverIndices(Map<String, Integer> col) {
        ColIndices idx = new ColIndices();

        idx.municipio    = primeroDeSet(col, COL_MUNICIPIO);
        idx.fecha        = primeroDeSet(col, COL_FECHA);
        idx.valorFactura = primero(col, COL_VALOR_FACTURA_PRIORIDAD);
        idx.mora         = primero(col, COL_MORA_PRIORIDAD);
        idx.saldo        = primero(col, COL_SALDO_PRIORIDAD);
        idx.total        = primero(col, COL_TOTAL_PRIORIDAD);
        idx.estrato      = primeroDeSet(col, COL_ESTRATO);
        idx.servicio     = primeroDeSet(col, COL_SERVICIO);
        idx.direccion    = primeroDeSet(col, COL_DIRECCION);
        idx.barrio       = primeroDeSet(col, COL_BARRIO);
        idx.contrato     = primeroDeSet(col, COL_CONTRATO);
        idx.mesesMora    = primeroDeSet(col, COL_MESES_MORA);
        idx.medidor      = primeroDeSet(col, COL_MEDIDOR);
        idx.codigo       = primeroDeSet(col, COL_CODIGO);
        idx.nombre       = primeroDeSet(col, COL_NOMBRE);
        idx.periodo      = primeroDeSet(col, COL_PERIODO);

        // Si COL_VALOR_FACTURA_PRIORIDAD eligió "valor" pero COL_TOTAL_PRIORIDAD
        // también eligió "valor", dejar solo total para no duplicar.
        if (idx.valorFactura != null && idx.total != null
                && idx.valorFactura.equals(idx.total)) {
            idx.valorFactura = null;
        }

        System.out.println("=== Índices resueltos ===");
        System.out.println("  municipio="    + idx.municipio
                + " fecha="       + idx.fecha
                + " valorFactura=" + idx.valorFactura
                + " mora="        + idx.mora
                + " saldo="       + idx.saldo
                + " total="       + idx.total
                + " estrato="     + idx.estrato
                + " mesesMora="   + idx.mesesMora);

        return idx;
    }

    // ── Parseo de una fila ─────────────────────────────────────────────────────

    private static Factura parsearFila(Row row, int numFila,
                                        ColIndices idx,
                                        String tipoFuente, String nombreArchivo) {
        Factura f = new Factura();

        // ── MUNICIPIO (obligatorio) ──────────────────────────────────────────
        String municipioRaw = obtenerCelda(row, idx.municipio);
        if (municipioRaw == null || municipioRaw.isBlank()) {
            throw new EpqException(ErrorCode.DATOS_INCOMPLETOS,
                    "Municipio vacío en fila " + numFila);
        }
        String municipioNorm = MunicipioConstantes.normalizar(municipioRaw);
        f.setMunicipio(municipioNorm != null ? municipioNorm : municipioRaw.trim().toUpperCase());

        // ── FECHA (obligatoria) ──────────────────────────────────────────────
        String fechaStr = obtenerCelda(row, idx.fecha);
        if (fechaStr == null || fechaStr.isBlank()) {
            throw new EpqException(ErrorCode.EXCEL_FECHA_INVALIDA,
                    "Fecha vacía en fila " + numFila);
        }
        LocalDate fechaFactura = parsearFecha(fechaStr.trim(), numFila);
        f.setFechaFactura(fechaFactura);
        f.setFechaVencimiento(fechaFactura.plusDays(30));

        // ── PERÍODO / MES REFERENCIA ─────────────────────────────────────────
        // Primero intenta el campo PERIODO explícito, si no lo tiene usa la fecha
        String periodoStr = obtenerCelda(row, idx.periodo);
        if (periodoStr != null && !periodoStr.isBlank()) {
            String mesRef = parsearPeriodo(periodoStr, fechaFactura);
            f.setMesReferencia(mesRef);
        } else {
            f.setMesReferencia(fechaFactura.getYear()
                    + "-" + String.format("%02d", fechaFactura.getMonthValue()));
        }

        // ── VALORES MONETARIOS ───────────────────────────────────────────────

        // Valor factura (cargo del mes)
        BigDecimal valorFactura = parsearBDSeguro(obtenerCelda(row, idx.valorFactura), numFila);
        f.setValorFactura(valorFactura);

        // Mora (valor del período)
        BigDecimal valorMora = parsearBDSeguro(obtenerCelda(row, idx.mora), numFila);
        f.setValorMora(valorMora);

        // Saldo de mora acumulado
        BigDecimal saldoMora = parsearBDSeguro(obtenerCelda(row, idx.saldo), numFila);
        f.setSaldoMora(saldoMora);

        // Total a pagar
        // Preferimos el VALOR TOTAL del archivo si existe; si no, calculamos.
        String totalStr = obtenerCelda(row, idx.total);
        if (totalStr != null && !totalStr.isBlank() && !totalStr.equals("0")) {
            try {
                BigDecimal totalArchivo = parsearBigDecimal(totalStr.trim(), numFila);
                f.setTotalPagar(totalArchivo);
                // Si no teníamos valorFactura, usamos el total como base
                if (valorFactura.compareTo(BigDecimal.ZERO) == 0) {
                    f.setValorFactura(totalArchivo.subtract(valorMora));
                }
            } catch (Exception e) {
                f.setTotalPagar(valorFactura.add(valorMora));
            }
        } else {
            f.setTotalPagar(valorFactura.add(valorMora));
        }

        // ── ESTRATO ──────────────────────────────────────────────────────────
        String estratoStr = obtenerCelda(row, idx.estrato);
        if (estratoStr != null && !estratoStr.isBlank()) {
            try {
                int estrato = (int) Double.parseDouble(estratoStr.trim());
                if (estrato >= 1 && estrato <= 6) f.setEstrato(estrato);
            } catch (NumberFormatException ignored) {}
        }

        // ── MESES DE MORA ─────────────────────────────────────────────────────
        String mesesStr = obtenerCelda(row, idx.mesesMora);
        if (mesesStr != null && !mesesStr.isBlank()) {
            try {
                f.setMesesMora((int) Double.parseDouble(mesesStr.trim()));
            } catch (NumberFormatException ignored) {}
        }

        // ── CAMPOS DE TEXTO OPCIONALES ────────────────────────────────────────
        setIfPresent(f::setNombreServicio,    obtenerCelda(row, idx.servicio));
        setIfPresent(f::setDireccionServicio, obtenerCelda(row, idx.direccion));
        setIfPresent(f::setBarrio,            obtenerCelda(row, idx.barrio));
        setIfPresent(f::setNumeroContrato,    obtenerCelda(row, idx.contrato));
        setIfPresent(f::setNumeroMedidor,     obtenerCelda(row, idx.medidor));
        setIfPresent(f::setCodigoCliente,     obtenerCelda(row, idx.codigo));
        setIfPresent(f::setNombreCliente,     obtenerCelda(row, idx.nombre));

        // ── ESTADO DE PAGO ────────────────────────────────────────────────────
        // VENCIDO si tiene mora/saldo o la fecha de vencimiento ya pasó
        boolean tieneDeuda = valorMora.compareTo(BigDecimal.ZERO) > 0
                || saldoMora.compareTo(BigDecimal.ZERO) > 0
                || (f.getMesesMora() != null && f.getMesesMora() > 0);
        boolean vencida = f.getFechaVencimiento().isBefore(LocalDate.now());
        f.setEstadoPago(tieneDeuda || vencida ? "VENCIDO" : "PENDIENTE");

        // ── TRAZABILIDAD ──────────────────────────────────────────────────────
        f.setTipoFuente(tipoFuente);
        f.setNombreArchivoOrigen(nombreArchivo);
        f.setFechaCarga(LocalDate.now());
        f.setTipoArchivoOrigen("EXCEL");

        return f;
    }

    // ── Helpers de localización de encabezados ─────────────────────────────────

    /**
     * Busca la fila de encabezados. Algunos archivos tienen filas de título
     * arriba de los encabezados reales. Busca la primera fila que contenga
     * la palabra "MUNICIPIO" o "CODIGO" en alguna celda.
     */
    private static int encontrarFilaEncabezado(Sheet sheet) {
        int maxBusqueda = Math.min(10, sheet.getLastRowNum());
        for (int i = 0; i <= maxBusqueda; i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            for (Cell cell : row) {
                String val = obtenerValorCeldaComoTexto(cell);
                if (val == null) continue;
                String norm = normalizarNombreColumna(val);
                if (norm.equals("municipio") || norm.equals("codigo")
                        || norm.equals("fecharecaudo") || norm.equals("fechapago")) {
                    return i;
                }
            }
        }
        return 0; // fallback: primera fila
    }

    // ── Parseo de período ──────────────────────────────────────────────────────

    /**
     * Convierte el valor del campo PERIODO a formato "YYYY-MM".
     * Soporta: "septiembre 2024", "09/2024", "2024-09", "SEPTIEMBRE 2024".
     */
    private static String parsearPeriodo(String periodo, LocalDate fechaFallback) {
        if (periodo == null || periodo.isBlank()) {
            return fechaFallback.getYear() + "-"
                    + String.format("%02d", fechaFallback.getMonthValue());
        }
        String p = periodo.trim();

        // Formato MM/YYYY
        if (p.matches("\\d{1,2}/\\d{4}")) {
            String[] partes = p.split("/");
            return partes[1] + "-" + String.format("%02d", Integer.parseInt(partes[0]));
        }

        // Formato YYYY-MM
        if (p.matches("\\d{4}-\\d{2}")) return p;

        // Formato "nombre_mes año" (ej: "septiembre 2024")
        String[] mesesNombre = {"enero","febrero","marzo","abril","mayo","junio",
                                "julio","agosto","septiembre","octubre","noviembre","diciembre"};
        String pLower = MunicipioConstantes.eliminarTildes(p).toLowerCase();
        for (int i = 0; i < mesesNombre.length; i++) {
            if (pLower.contains(mesesNombre[i])) {
                // extraer año
                String[] partes = pLower.split("\\s+");
                for (String parte : partes) {
                    if (parte.matches("\\d{4}")) {
                        return parte + "-" + String.format("%02d", i + 1);
                    }
                }
            }
        }

        // Si no se puede parsear, usar la fecha de factura
        return fechaFallback.getYear() + "-"
                + String.format("%02d", fechaFallback.getMonthValue());
    }

    // ── Helpers generales ──────────────────────────────────────────────────────

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
                if (!normalizado.isBlank()) {
                    mapa.put(normalizado, cell.getColumnIndex());
                }
            }
        }
        return mapa;
    }

    /** Normaliza nombre de columna: sin tildes, minúsculas, sin espacios/guiones */
    public static String normalizarNombreColumna(String nombre) {
        if (nombre == null) return "";
        return MunicipioConstantes.eliminarTildes(nombre)
                .toLowerCase()
                .replaceAll("[\\s\\-_/\\.]+", "");
    }

    private static String obtenerCelda(Row row, Integer colIndex) {
        if (colIndex == null) return null;
        Cell cell = row.getCell(colIndex);
        return obtenerValorCeldaComoTexto(cell);
    }

    static String obtenerValorCeldaComoTexto(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING  -> {
                String s = cell.getStringCellValue();
                yield s.isBlank() ? null : s.trim();
            }
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double num = cell.getNumericCellValue();
                yield num == (long) num ? String.valueOf((long) num) : String.valueOf(num);
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf(cell.getNumericCellValue()); }
                catch (Exception e) {
                    try { yield cell.getStringCellValue(); }
                    catch (Exception e2) { yield null; }
                }
            }
            default -> null;
        };
    }

    private static boolean esFilaVacia(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String v = obtenerValorCeldaComoTexto(cell);
                if (v != null && !v.isBlank()) return false;
            }
        }
        return true;
    }

    @FunctionalInterface
    private interface Setter { void set(String v); }

    private static void setIfPresent(Setter setter, String valor) {
        if (valor != null && !valor.isBlank()) setter.set(valor.trim());
    }

    private static BigDecimal parsearBDSeguro(String valorStr, int numFila) {
        if (valorStr == null || valorStr.isBlank()) return BigDecimal.ZERO;
        try { return parsearBigDecimal(valorStr.trim(), numFila); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    // ── Parseo de fecha ────────────────────────────────────────────────────────

    public static LocalDate parsearFecha(String fechaStr, int numFila) {
        String s = fechaStr.trim();
        for (DateTimeFormatter fmt : List.of(FMT_ISO, FMT_DDMMYYYY, FMT_DDMMYYYY2)) {
            try { return LocalDate.parse(s, fmt); } catch (DateTimeParseException ignored) {}
        }
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

        // Quitar espacios internos (ej: "1 234 567")
        s = s.replaceAll("\\s+", "");

        if (s.isEmpty() || s.equals("-") || s.equalsIgnoreCase("N/A")) {
            return BigDecimal.ZERO;
        }

        // Formato colombiano: 1.234.567,89 → 1234567.89
        if (s.contains(",") && s.contains(".")) {
            // Determinar cuál es miles y cuál es decimal por posición
            int lastDot   = s.lastIndexOf('.');
            int lastComma = s.lastIndexOf(',');
            if (lastComma > lastDot) {
                // coma es decimal: 1.234,56
                s = s.replace(".", "").replace(",", ".");
            } else {
                // punto es decimal: 1,234.56
                s = s.replace(",", "");
            }
        } else if (s.contains(",") && !s.contains(".")) {
            // Sólo coma: puede ser decimal (1234,56) o miles (1.234)
            // Si hay exactamente 3 dígitos después de la coma → miles; si no → decimal
            int idx = s.lastIndexOf(',');
            if (s.length() - idx - 1 == 3) {
                s = s.replace(",", ""); // miles
            } else {
                s = s.replace(",", "."); // decimal
            }
        } else if (s.contains(".")) {
            // Solo punto: si hay más de un punto o el punto tiene 3 dígitos tras él → miles
            long cntDots = s.chars().filter(c -> c == '.').count();
            int idx = s.lastIndexOf('.');
            if (cntDots > 1 || s.length() - idx - 1 == 3) {
                s = s.replace(".", ""); // miles
            }
            // si no, es decimal → dejar como está
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
