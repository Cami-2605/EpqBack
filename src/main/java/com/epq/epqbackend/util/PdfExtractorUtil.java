package com.epq.epqbackend.util;

import com.epq.epqbackend.exception.EpqException;
import com.epq.epqbackend.exception.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidad para extraer información estructurada de archivos PDF.
 *
 * Estrategia de extracción:
 * 1. Extrae el texto completo del PDF con PDFBox.
 * 2. Busca patrones conocidos (municipio, fecha, valores) usando expresiones regulares.
 * 3. Devuelve una lista de mapas, uno por registro detectado.
 *
 * Limitaciones: solo funciona con PDFs de texto (no imágenes escaneadas).
 * Los PDFs escaneados devolverán una lista vacía con un aviso.
 */
public final class PdfExtractorUtil {

    private PdfExtractorUtil() {}

    // ── Patrones de fecha ──────────────────────────────────────────────────────
    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("MM/yyyy"),
            DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "CO"))
    );

    // ── Patrones de valor monetario ────────────────────────────────────────────
    private static final Pattern PATRON_VALOR =
            Pattern.compile("\\$?\\s*([\\d]{1,3}(?:[.,][\\d]{3})*(?:[.,][\\d]{1,2})?)");

    // ── Patrones de municipio (busca el nombre en el texto) ───────────────────
    private static final Pattern PATRON_MUNICIPIO;
    static {
        String municipiosAlternativa = String.join("|",
                "BUENAVISTA", "CIRCASIA", "FILANDIA", "GENOVA", "GÉNOVA",
                "LA TEBAIDA", "MONTENEGRO", "PIJAO", "QUIMBAYA", "SALENTO"
        );
        PATRON_MUNICIPIO = Pattern.compile(
                "(?i)(?:municipio[:\\s]+)?(" + municipiosAlternativa + ")",
                Pattern.CASE_INSENSITIVE
        );
    }

    // ── Patrones de estrato ────────────────────────────────────────────────────
    private static final Pattern PATRON_ESTRATO =
            Pattern.compile("(?i)estrato\\s*[:\\-]?\\s*([1-6])");

    // ── Patrones de mora ───────────────────────────────────────────────────────
    private static final Pattern PATRON_MORA =
            Pattern.compile("(?i)(?:mora|saldo\\s+mora|valor\\s+mora)[:\\s]+\\$?\\s*([\\d.,]+)");

    private static final Pattern PATRON_SALDO_MORA =
            Pattern.compile("(?i)saldo\\s+(?:de\\s+)?mora[:\\s]+\\$?\\s*([\\d.,]+)");

    // ── Patrones de dirección ──────────────────────────────────────────────────
    private static final Pattern PATRON_DIRECCION =
            Pattern.compile("(?i)(?:direcci[oó]n|direc\\.|calle|carrera|cr|cl|av\\.?)[:\\s]+([\\w\\s#\\-\\.]+?)(?:\\n|\\r|,|$)");

    // ── Patrones de contrato ───────────────────────────────────────────────────
    private static final Pattern PATRON_CONTRATO =
            Pattern.compile("(?i)(?:contrato|n[úu]m(?:ero)?[\\s.]*contrato|cuenta)[:\\s]+([\\w\\-]+)");

    // ── Patrones de fecha de factura ───────────────────────────────────────────
    private static final Pattern PATRON_FECHA_FACTURA =
            Pattern.compile("(?i)(?:fecha\\s+(?:de\\s+)?factura|fecha\\s+emisi[oó]n|emitida?)[:\\s]+(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}|\\d{4}[/\\-]\\d{2}[/\\-]\\d{2})");

    private static final Pattern PATRON_PERIODO =
            Pattern.compile("(?i)(?:per[ií]odo|mes\\s+de\\s+facturaci[oó]n|mes)[:\\s]+(\\w+\\s+\\d{4}|\\d{2}/\\d{4}|\\d{4}-\\d{2})");

    // ── Patrones de valor de factura ───────────────────────────────────────────
    private static final Pattern PATRON_VALOR_FACTURA =
            Pattern.compile("(?i)(?:valor\\s+(?:de\\s+)?(?:la\\s+)?factura|total\\s+a\\s+pagar|valor\\s+total)[:\\s]+\\$?\\s*([\\d.,]+)");

    /**
     * Extrae el texto completo de un PDF y lo devuelve como String.
     * Lanza EpqException si el archivo está corrupto o es una imagen.
     */
    public static String extraerTexto(MultipartFile archivo) {
        validarArchivo(archivo);
        try {
            byte[] bytes = archivo.getBytes();
            try (PDDocument doc = Loader.loadPDF(bytes)) {
                if (doc.isEncrypted()) {
                    throw new EpqException(ErrorCode.PDF_NO_LEGIBLE,
                            "El PDF está protegido con contraseña y no puede ser procesado");
                }

                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                String texto = stripper.getText(doc);

                if (texto == null || texto.trim().length() < 20) {
                    throw new EpqException(ErrorCode.PDF_SIN_DATOS_ESTRUCTURADOS,
                            "El PDF parece ser una imagen escaneada. No se pudo extraer texto. " +
                            "Considere usar un PDF con texto seleccionable.");
                }
                return texto;
            }
        } catch (EpqException e) {
            throw e;
        } catch (Exception e) {
            throw new EpqException(ErrorCode.ARCHIVO_CORRUPTO,
                    "No se pudo abrir el PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Intenta extraer registros de facturación del texto de un PDF.
     * Devuelve una lista (puede estar vacía si no encuentra datos estructurados).
     *
     * Cada mapa en la lista contiene las claves que se pudieron detectar:
     * municipio, fechaFactura, mesReferencia, valorFactura, valorMora, saldoMora,
     * estrato, direccion, contrato.
     */
    public static List<Map<String, String>> extraerRegistros(String texto) {
        List<Map<String, String>> registros = new ArrayList<>();

        // Estrategia 1: intenta detectar si hay una tabla estructurada
        // (líneas con patrón: municipio + datos en columnas)
        List<Map<String, String>> porTabla = extraerDeTabla(texto);
        if (!porTabla.isEmpty()) {
            return porTabla;
        }

        // Estrategia 2: extracción por campos individuales del documento
        Map<String, String> registro = extraerCamposGenerales(texto);
        if (!registro.isEmpty()) {
            registros.add(registro);
        }

        return registros;
    }

    /**
     * Intenta extraer datos cuando el PDF tiene una estructura tabular:
     * cada línea representa una factura con municipio y valores.
     */
    private static List<Map<String, String>> extraerDeTabla(String texto) {
        List<Map<String, String>> registros = new ArrayList<>();
        String[] lineas = texto.split("\\r?\\n");

        for (String linea : lineas) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;

            // Busca si la línea contiene un municipio válido
            String municipio = detectarMunicipio(linea);
            if (municipio == null) continue;

            // La línea tiene un municipio → intentar extraer valores de la misma línea
            Map<String, String> registro = new LinkedHashMap<>();
            registro.put("municipio", municipio);

            // Extraer números de la línea (los primeros 2-3 son los valores monetarios)
            List<BigDecimal> valores = extraerValoresDeLínea(linea);
            if (!valores.isEmpty()) {
                registro.put("valorFactura", valores.get(0).toPlainString());
                if (valores.size() >= 2) {
                    registro.put("valorMora", valores.get(1).toPlainString());
                }
                if (valores.size() >= 3) {
                    registro.put("saldoMora", valores.get(2).toPlainString());
                }
            }

            // Estrato en la línea
            Matcher mEstrato = PATRON_ESTRATO.matcher(linea);
            if (mEstrato.find()) {
                registro.put("estrato", mEstrato.group(1));
            }

            // Fecha/periodo en la línea
            String fecha = detectarFechaEnTexto(linea);
            if (fecha != null) {
                registro.put("fechaFactura", fecha);
            }

            registros.add(registro);
        }

        return registros;
    }

    /**
     * Extrae campos del documento completo cuando no hay tabla línea por línea.
     */
    private static Map<String, String> extraerCamposGenerales(String texto) {
        Map<String, String> registro = new LinkedHashMap<>();
        String textoNorm = texto.toUpperCase().replace("\n", " ").replace("\r", " ");

        // Municipio
        String municipio = detectarMunicipio(textoNorm);
        if (municipio != null) {
            registro.put("municipio", municipio);
        }

        // Valor de factura
        Matcher mValor = PATRON_VALOR_FACTURA.matcher(texto);
        if (mValor.find()) {
            registro.put("valorFactura", normalizarValor(mValor.group(1)));
        }

        // Mora
        Matcher mMora = PATRON_MORA.matcher(texto);
        if (mMora.find()) {
            registro.put("valorMora", normalizarValor(mMora.group(1)));
        }

        // Saldo de mora
        Matcher mSaldo = PATRON_SALDO_MORA.matcher(texto);
        if (mSaldo.find()) {
            registro.put("saldoMora", normalizarValor(mSaldo.group(1)));
        }

        // Estrato
        Matcher mEstrato = PATRON_ESTRATO.matcher(texto);
        if (mEstrato.find()) {
            registro.put("estrato", mEstrato.group(1));
        }

        // Dirección
        Matcher mDir = PATRON_DIRECCION.matcher(texto);
        if (mDir.find()) {
            registro.put("direccion", mDir.group(1).trim());
        }

        // Contrato
        Matcher mContrato = PATRON_CONTRATO.matcher(texto);
        if (mContrato.find()) {
            registro.put("contrato", mContrato.group(1).trim());
        }

        // Fecha de factura
        Matcher mFecha = PATRON_FECHA_FACTURA.matcher(texto);
        if (mFecha.find()) {
            registro.put("fechaFactura", mFecha.group(1).trim());
        }

        // Período
        Matcher mPeriodo = PATRON_PERIODO.matcher(texto);
        if (mPeriodo.find()) {
            registro.put("mesReferencia", mPeriodo.group(1).trim());
        }

        return registro;
    }

    // ── Métodos auxiliares ─────────────────────────────────────────────────────

    private static String detectarMunicipio(String texto) {
        Matcher m = PATRON_MUNICIPIO.matcher(texto);
        if (m.find()) {
            return MunicipioConstantes.normalizar(m.group(1));
        }
        return null;
    }

    private static String detectarFechaEnTexto(String texto) {
        // Patrón simple para líneas de tabla: dd/mm/yyyy o yyyy-mm-dd
        Pattern p = Pattern.compile("\\b(\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}|\\d{4}[/\\-]\\d{2}[/\\-]\\d{2})\\b");
        Matcher m = p.matcher(texto);
        if (m.find()) return m.group(1);
        return null;
    }

    private static List<BigDecimal> extraerValoresDeLínea(String linea) {
        List<BigDecimal> valores = new ArrayList<>();
        Matcher m = PATRON_VALOR.matcher(linea);
        while (m.find()) {
            try {
                String val = normalizarValor(m.group(1));
                BigDecimal bd = new BigDecimal(val);
                // Ignora números pequeños que probablemente son estratos o conteos
                if (bd.compareTo(BigDecimal.valueOf(100)) >= 0) {
                    valores.add(bd);
                }
            } catch (NumberFormatException ignored) {}
        }
        return valores;
    }

    private static String normalizarValor(String val) {
        if (val == null) return "0";
        val = val.trim().replace("$", "").replace(" ", "");
        // Formato colombiano: 1.234.567,89 → 1234567.89
        if (val.contains(",") && val.contains(".")) {
            val = val.replace(".", "").replace(",", ".");
        } else if (val.contains(",") && !val.contains(".")) {
            val = val.replace(",", ".");
        } else if (val.contains(".") && val.indexOf(".") < val.length() - 3) {
            // Punto como separador de miles: 1.234.567 → 1234567
            val = val.replace(".", "");
        }
        return val;
    }

    private static void validarArchivo(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new EpqException(ErrorCode.ARCHIVO_VACIO, "El archivo PDF está vacío");
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre == null || !nombre.toLowerCase().endsWith(".pdf")) {
            throw new EpqException(ErrorCode.ARCHIVO_FORMATO_NO_SOPORTADO,
                    "El archivo no tiene extensión .pdf");
        }
    }
}
