package com.epq.epqbackend.service;

import com.epq.epqbackend.dto.FiltroInformeDto;
import com.epq.epqbackend.exception.EpqException;
import com.epq.epqbackend.exception.ErrorCode;
import com.epq.epqbackend.repository.FacturaRepository;
import com.epq.epqbackend.util.MunicipioConstantes;

// Apache POI — imports explícitos para evitar ambigüedad con OpenPDF
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

// OpenPDF — imports explícitos para evitar ambigüedad con java.util.List
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Genera los informes analíticos del sistema EPQ:
 *
 *  1. TOP_MOROSOS         — Top 40 (o N) con mayor edad de mora y valor
 *  2. RECAUDO_GENERAL     — Recaudo total agrupado por mes y municipio
 *  3. EDAD_MORA           — Distribución de morosos por rango de meses
 *  4. RECAUDO_MUNICIPIO   — Recaudo total por municipio
 *  5. RECAUDO_SERVICIO    — Recaudo por tipo de servicio (gas, agua, etc.)
 *  6. MOROSOS_POR_ESTRATO — Top morosos dentro de cada estrato
 *  7. PAGOS_POR_ESTRATO   — Comparativa de pagos estratos 1-2-3
 *  8. RECAUDO_DIARIO      — Recaudo agrupado por día
 *  9. PROYECCION          — Promedio mensual y proyección a N meses
 */
@Service
public class InformeAnaliticoService {

    @Autowired
    private FacturaRepository facturaRepository;

    // ── Constantes visuales ────────────────────────────────────────────────────
    private static final Color AZUL_EPQ    = new Color(0,  84, 166);
    private static final Color ROJO_MOROSO = new Color(255, 199, 206);
    private static final Color GRIS_FILA   = new Color(242, 242, 242);
    private static final Color VERDE_OK    = new Color(198, 239, 206);
    private static final Color NARANJA     = new Color(255, 235, 156);

    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat FMT_MONEDA;
    static {
        FMT_MONEDA = NumberFormat.getNumberInstance(new Locale("es", "CO"));
        FMT_MONEDA.setMinimumFractionDigits(0);
        FMT_MONEDA.setMaximumFractionDigits(0);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // API PÚBLICA
    // ══════════════════════════════════════════════════════════════════════════

    /** Genera el informe en Excel. */
    public byte[] generarExcel(FiltroInformeDto filtros) {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            List<String> municipios = normalizarMunicipios(filtros.getMunicipios());
            List<Integer> estratos  = filtros.getEstratos();
            List<String> archivos   = filtros.getNombreArchivos();
            String tipo             = filtros.getTipoInforme().toUpperCase();

            // Selección de hojas según tipo
            List<String> tipos = tipo.equals("TODOS")
                    ? List.of("TOP_MOROSOS","RECAUDO_GENERAL","EDAD_MORA",
                              "RECAUDO_MUNICIPIO","RECAUDO_SERVICIO",
                              "MOROSOS_POR_ESTRATO","PAGOS_POR_ESTRATO",
                              "RECAUDO_DIARIO","PROYECCION")
                    : List.of(tipo);

            boolean algunaHoja = false;
            for (String t : tipos) {
                try {
                    switch (t) {
                        case "TOP_MOROSOS"         -> { hojaTopMorosos(wb, filtros, municipios, estratos, archivos);   algunaHoja = true; }
                        case "RECAUDO_GENERAL"     -> { hojaRecaudoGeneral(wb, filtros, municipios, archivos);         algunaHoja = true; }
                        case "EDAD_MORA"           -> { hojaEdadMora(wb, municipios, archivos);                        algunaHoja = true; }
                        case "RECAUDO_MUNICIPIO"   -> { hojaRecaudoMunicipio(wb, filtros, municipios, archivos);       algunaHoja = true; }
                        case "RECAUDO_SERVICIO"    -> { hojaRecaudoServicio(wb, filtros, municipios, archivos);        algunaHoja = true; }
                        case "MOROSOS_POR_ESTRATO" -> { hojaMorososPorEstrato(wb, municipios, archivos);               algunaHoja = true; }
                        case "PAGOS_POR_ESTRATO"   -> { hojaPagosPorEstrato(wb, filtros, municipios, archivos);        algunaHoja = true; }
                        case "RECAUDO_DIARIO"      -> { hojaRecaudoDiario(wb, filtros, municipios, archivos);          algunaHoja = true; }
                        case "PROYECCION"          -> { hojaProyeccion(wb, filtros, municipios, archivos);             algunaHoja = true; }
                        default -> throw new EpqException(ErrorCode.REPORTE_TIPO_INVALIDO,
                                "Tipo de informe no reconocido: " + t);
                    }
                } catch (EpqException e) { throw e; }
                catch (Exception e) {
                    // Si falla una hoja, continuar con las demás
                    Sheet err = wb.createSheet("Error-" + t.substring(0, Math.min(t.length(), 20)));
                    err.createRow(0).createCell(0).setCellValue("Error generando hoja: " + e.getMessage());
                }
            }

            if (!algunaHoja) {
                throw new EpqException(ErrorCode.REPORTE_SIN_DATOS,
                        "No se pudo generar ningún informe con los filtros seleccionados");
            }

            wb.write(out);
            return out.toByteArray();

        } catch (EpqException e) { throw e; }
        catch (Exception e) {
            throw new EpqException(ErrorCode.REPORTE_ERROR_GENERACION,
                    "Error generando el informe Excel: " + e.getMessage(), e);
        }
    }

    /** Genera el informe en PDF. */
    public byte[] generarPdf(FiltroInformeDto filtros) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 20, 20, 40, 30);
            PdfWriter.getInstance(doc, out);
            doc.open();

            List<String> municipios = normalizarMunicipios(filtros.getMunicipios());
            List<Integer> estratos  = filtros.getEstratos();
            List<String> archivos   = filtros.getNombreArchivos();
            String tipo             = filtros.getTipoInforme().toUpperCase();

            agregarEncabezadoPdf(doc, filtros);

            List<String> tipos = tipo.equals("TODOS")
                    ? List.of("TOP_MOROSOS","RECAUDO_GENERAL","EDAD_MORA",
                              "RECAUDO_MUNICIPIO","RECAUDO_SERVICIO",
                              "MOROSOS_POR_ESTRATO","PAGOS_POR_ESTRATO",
                              "RECAUDO_DIARIO","PROYECCION")
                    : List.of(tipo);

            for (String t : tipos) {
                try {
                    switch (t) {
                        case "TOP_MOROSOS"         -> pdfTopMorosos(doc, filtros, municipios, estratos, archivos);
                        case "RECAUDO_GENERAL"     -> pdfRecaudoGeneral(doc, filtros, municipios, archivos);
                        case "EDAD_MORA"           -> pdfEdadMora(doc, municipios, archivos);
                        case "RECAUDO_MUNICIPIO"   -> pdfRecaudoMunicipio(doc, filtros, municipios, archivos);
                        case "RECAUDO_SERVICIO"    -> pdfRecaudoServicio(doc, filtros, municipios, archivos);
                        case "MOROSOS_POR_ESTRATO" -> pdfMorososPorEstrato(doc, municipios, archivos);
                        case "PAGOS_POR_ESTRATO"   -> pdfPagosPorEstrato(doc, filtros, municipios, archivos);
                        case "RECAUDO_DIARIO"      -> pdfRecaudoDiario(doc, filtros, municipios, archivos);
                        case "PROYECCION"          -> pdfProyeccion(doc, filtros, municipios, archivos);
                        default -> {}
                    }
                    doc.add(new Paragraph(" "));
                } catch (Exception e) {
                    doc.add(new Paragraph("Error en sección " + t + ": " + e.getMessage()));
                }
            }

            doc.close();
            return out.toByteArray();

        } catch (EpqException e) { throw e; }
        catch (Exception e) {
            throw new EpqException(ErrorCode.REPORTE_ERROR_GENERACION,
                    "Error generando el informe PDF: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HOJA 1: TOP N MOROSOS
    // ══════════════════════════════════════════════════════════════════════════

    private void hojaTopMorosos(XSSFWorkbook wb, FiltroInformeDto filtros,
                                 List<String> municipios, List<Integer> estratos,
                                 List<String> archivos) {
        int topN = filtros.getTopN();
        List<Object[]> rows = facturaRepository.findTopMorosos(municipios, estratos, archivos);

        Sheet sheet = wb.createSheet("Top " + topN + " Morosos");
        CellStyle hSt  = headerStyle(wb, AZUL_EPQ);
        CellStyle mSt  = moedaStyle(wb);
        CellStyle rSt  = fondoStyle(wb, ROJO_MOROSO);

        int row = 0;
        titulo(sheet, wb, "TOP " + topN + " USUARIOS MOROSOS (Mayor edad de mora)", 10, row++);
        row++;

        String[] cols = {"#", "Código", "Nombre", "Municipio", "Estrato",
                         "Dirección", "Meses Mora", "Valor Mora", "Saldo Mora",
                         "Total a Pagar"};
        encabezados(sheet, row++, cols, hSt);

        int pos = 1;
        for (Object[] r : rows) {
            if (pos > topN) break;
            Row xr = sheet.createRow(row++);
            CellStyle cst = pos <= 10 ? rSt : null;   // top 10 resaltados en rojo

            celda(xr, 0, String.valueOf(pos++), cst);
            celda(xr, 1, str(r[0]), cst);
            celda(xr, 2, str(r[1]), cst);
            celda(xr, 3, str(r[2]), cst);
            celda(xr, 4, r[3] != null ? "E" + r[3] : "N/A", cst);
            celda(xr, 5, str(r[4]), cst);
            celdaNum(xr, 6, r[5] != null ? ((Number) r[5]).doubleValue() : 0, null);
            celdaNum(xr, 7, bd(r[6]), mSt);
            celdaNum(xr, 8, bd(r[7]), mSt);
            celdaNum(xr, 9, bd(r[8]), mSt);
        }

        // Fila de totales
        List<Object[]> subset = rows.stream().limit(topN).collect(Collectors.toList());
        BigDecimal totalMora  = subset.stream().map(r -> toBD(r[6])).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSaldo = subset.stream().map(r -> toBD(r[7])).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTotal = subset.stream().map(r -> toBD(r[8])).reduce(BigDecimal.ZERO, BigDecimal::add);
        Row totalRow = sheet.createRow(row);
        CellStyle boldSt = boldStyle(wb);
        celda(totalRow, 0, "TOTAL", boldSt);
        celda(totalRow, 1, pos - 1 + " registros", null);
        celdaNum(totalRow, 7, totalMora.doubleValue(),  mSt);
        celdaNum(totalRow, 8, totalSaldo.doubleValue(), mSt);
        celdaNum(totalRow, 9, totalTotal.doubleValue(), mSt);

        autoSize(sheet, cols.length);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HOJA 2: RECAUDO GENERAL
    // ══════════════════════════════════════════════════════════════════════════

    private void hojaRecaudoGeneral(XSSFWorkbook wb, FiltroInformeDto filtros,
                                     List<String> municipios, List<String> archivos) {
        List<Object[]> rows = facturaRepository.findRecaudoGeneral(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios, archivos);

        Sheet sheet = wb.createSheet("Recaudo General");
        CellStyle hSt = headerStyle(wb, AZUL_EPQ);
        CellStyle mSt = moedaStyle(wb);
        CellStyle pSt = fondoStyle(wb, GRIS_FILA);

        int row = 0;
        titulo(sheet, wb, "RECAUDO GENERAL POR PERÍODO Y MUNICIPIO", 5, row++);
        row++;

        String[] cols = {"Período", "Municipio", "Servicio", "Total Recaudado", "# Facturas"};
        encabezados(sheet, row++, cols, hSt);

        BigDecimal granTotal = BigDecimal.ZERO;
        long granConteo = 0;
        int idx = 0;
        String mesActual = "";

        for (Object[] r : rows) {
            Row xr = sheet.createRow(row++);
            String mes = str(r[0]);
            CellStyle cst = (idx++ % 2 == 0) ? null : pSt;

            // Separador visual cuando cambia el mes
            if (!mes.equals(mesActual)) {
                mesActual = mes;
            }

            celda(xr, 0, mes, cst);
            celda(xr, 1, str(r[1]), cst);
            celda(xr, 2, str(r[2]), cst);
            celdaNum(xr, 3, bd(r[3]), mSt);
            celdaNum(xr, 4, r[4] != null ? ((Number) r[4]).doubleValue() : 0, null);

            granTotal = granTotal.add(toBD(r[3]));
            granConteo += r[4] != null ? ((Number) r[4]).longValue() : 0;
        }

        // Gran total
        row++;
        Row totalRow = sheet.createRow(row);
        CellStyle boldSt = boldStyle(wb);
        celda(totalRow, 0, "GRAN TOTAL", boldSt);
        celdaNum(totalRow, 3, granTotal.doubleValue(), mSt);
        celdaNum(totalRow, 4, (double) granConteo, null);

        autoSize(sheet, cols.length);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HOJA 3: EDAD DE LOS MOROSOS
    // ══════════════════════════════════════════════════════════════════════════

    private void hojaEdadMora(XSSFWorkbook wb, List<String> municipios, List<String> archivos) {
        List<Object[]> rows = facturaRepository.findDistribucionEdadMora(municipios, archivos);

        Sheet sheet = wb.createSheet("Edad de Mora");
        CellStyle hSt  = headerStyle(wb, AZUL_EPQ);
        CellStyle mSt  = moedaStyle(wb);
        CellStyle r1St = fondoStyle(wb, VERDE_OK);
        CellStyle r2St = fondoStyle(wb, NARANJA);
        CellStyle r3St = fondoStyle(wb, ROJO_MOROSO);

        int row = 0;
        titulo(sheet, wb, "DISTRIBUCIÓN DE MOROSOS POR EDAD DE MORA", 6, row++);
        row++;

        // Tabla 1: por rangos agrupados
        String[] colsRango = {"Rango (meses)", "Cantidad", "% del Total",
                              "Suma Saldo Mora", "Suma Valor Mora", "Promedio Saldo"};
        encabezados(sheet, row++, colsRango, hSt);

        // Agrupar en rangos: 1-3, 4-6, 7-12, 13-24, 25+
        Map<String, long[]> rangos = new LinkedHashMap<>();
        rangos.put("1 - 3 meses",   new long[]{1, 3});
        rangos.put("4 - 6 meses",   new long[]{4, 6});
        rangos.put("7 - 12 meses",  new long[]{7, 12});
        rangos.put("13 - 24 meses", new long[]{13, 24});
        rangos.put("25+ meses",     new long[]{25, Long.MAX_VALUE});

        // calcular totales por rango
        record Acum(long count, BigDecimal saldo, BigDecimal mora) {}
        Map<String, Acum> acumRangos = new LinkedHashMap<>();
        for (String k : rangos.keySet()) acumRangos.put(k, new Acum(0, BigDecimal.ZERO, BigDecimal.ZERO));

        long totalRegistros = 0;
        for (Object[] r : rows) {
            int meses = r[0] != null ? ((Number) r[0]).intValue() : 0;
            long cnt  = r[1] != null ? ((Number) r[1]).longValue() : 0;
            BigDecimal saldo = toBD(r[2]);
            BigDecimal mora  = toBD(r[3]);
            totalRegistros += cnt;

            for (Map.Entry<String, long[]> e : rangos.entrySet()) {
                long[] bounds = e.getValue();
                if (meses >= bounds[0] && meses <= bounds[1]) {
                    Acum prev = acumRangos.get(e.getKey());
                    acumRangos.put(e.getKey(), new Acum(
                            prev.count() + cnt,
                            prev.saldo().add(saldo.multiply(BigDecimal.valueOf(cnt))),
                            prev.mora().add(mora.multiply(BigDecimal.valueOf(cnt)))
                    ));
                    break;
                }
            }
        }

        int i = 0;
        for (Map.Entry<String, Acum> e : acumRangos.entrySet()) {
            Acum a = e.getValue();
            if (a.count() == 0) continue;
            Row xr = sheet.createRow(row++);
            // color por gravedad
            CellStyle cst = i == 0 ? r1St : (i <= 2 ? r2St : r3St);
            celda(xr, 0, e.getKey(), cst);
            celdaNum(xr, 1, (double) a.count(), null);
            double pct = totalRegistros > 0 ? (a.count() * 100.0 / totalRegistros) : 0;
            celda(xr, 2, String.format("%.1f%%", pct), null);
            celdaNum(xr, 3, a.saldo().doubleValue(), mSt);
            celdaNum(xr, 4, a.mora().doubleValue(), mSt);
            BigDecimal prom = a.count() > 0
                    ? a.saldo().divide(BigDecimal.valueOf(a.count()), 0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            celdaNum(xr, 5, prom.doubleValue(), mSt);
            i++;
        }

        // Totales
        row++;
        Row totalRow = sheet.createRow(row);
        CellStyle boldSt = boldStyle(wb);
        celda(totalRow, 0, "TOTAL", boldSt);
        celdaNum(totalRow, 1, (double) totalRegistros, boldSt);

        // Tabla 2: detalle mes a mes (fila por cada valor distinto de meses)
        row += 2;
        titulo(sheet, wb, "DETALLE POR MESES EXACTOS DE MORA", 5, row++);
        row++;
        String[] colsDetalle = {"Meses en Mora", "Cantidad", "Suma Saldo Mora",
                                "Suma Valor Mora", "Prom. Saldo"};
        encabezados(sheet, row++, colsDetalle, hSt);

        for (Object[] r : rows) {
            Row xr = sheet.createRow(row++);
            int meses  = r[0] != null ? ((Number) r[0]).intValue() : 0;
            long cnt   = r[1] != null ? ((Number) r[1]).longValue() : 0;
            BigDecimal saldoSum = toBD(r[2]);
            BigDecimal moraSum  = toBD(r[3]);
            BigDecimal prom = cnt > 0
                    ? saldoSum.divide(BigDecimal.valueOf(cnt), 0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            celdaNum(xr, 0, (double) meses, null);
            celdaNum(xr, 1, (double) cnt, null);
            celdaNum(xr, 2, saldoSum.doubleValue(), mSt);
            celdaNum(xr, 3, moraSum.doubleValue(), mSt);
            celdaNum(xr, 4, prom.doubleValue(), mSt);
        }

        autoSize(sheet, 6);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HOJA 4: RECAUDO POR MUNICIPIO
    // ══════════════════════════════════════════════════════════════════════════

    private void hojaRecaudoMunicipio(XSSFWorkbook wb, FiltroInformeDto filtros,
                                       List<String> municipios, List<String> archivos) {
        List<Object[]> rows = facturaRepository.sumTotalPorMunicipio(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios);

        Sheet sheet = wb.createSheet("Recaudo por Municipio");
        CellStyle hSt = headerStyle(wb, AZUL_EPQ);
        CellStyle mSt = moedaStyle(wb);
        CellStyle pSt = fondoStyle(wb, GRIS_FILA);

        int row = 0;
        titulo(sheet, wb, "RECAUDO TOTAL POR MUNICIPIO", 5, row++);
        row++;

        String[] cols = {"Municipio", "Total Recaudado", "Total Mora",
                         "Total Saldo Mora", "% del Total"};
        encabezados(sheet, row++, cols, hSt);

        // Calcular gran total primero para el porcentaje
        BigDecimal granTotal = rows.stream().map(r -> toBD(r[1])).reduce(BigDecimal.ZERO, BigDecimal::add);

        int idx = 0;
        for (Object[] r : rows) {
            Row xr = sheet.createRow(row++);
            CellStyle cst = (idx++ % 2 == 0) ? null : pSt;
            BigDecimal total = toBD(r[1]);
            double pct = granTotal.compareTo(BigDecimal.ZERO) > 0
                    ? total.divide(granTotal, 4, RoundingMode.HALF_UP)
                           .multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;

            celda(xr, 0, str(r[0]), cst);
            celdaNum(xr, 1, total.doubleValue(), mSt);
            celdaNum(xr, 2, bd(r[2]), mSt);
            celdaNum(xr, 3, bd(r[3]), mSt);
            celda(xr, 4, String.format("%.2f%%", pct), cst);
        }

        row++;
        Row tr = sheet.createRow(row);
        CellStyle boldSt = boldStyle(wb);
        celda(tr, 0, "GRAN TOTAL", boldSt);
        celdaNum(tr, 1, granTotal.doubleValue(), mSt);
        celda(tr, 4, "100.00%", boldSt);

        autoSize(sheet, cols.length);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HOJA 5: RECAUDO POR SERVICIO
    // ══════════════════════════════════════════════════════════════════════════

    private void hojaRecaudoServicio(XSSFWorkbook wb, FiltroInformeDto filtros,
                                      List<String> municipios, List<String> archivos) {
        List<Object[]> rows = facturaRepository.findRecaudoPorServicio(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios, archivos);

        Sheet sheet = wb.createSheet("Recaudo por Servicio");
        CellStyle hSt = headerStyle(wb, AZUL_EPQ);
        CellStyle mSt = moedaStyle(wb);

        int row = 0;
        titulo(sheet, wb, "RECAUDO TOTAL POR TIPO DE SERVICIO", 5, row++);
        row++;

        String[] cols = {"Servicio", "Total Recaudado", "# Facturas",
                         "Total Mora", "% del Total"};
        encabezados(sheet, row++, cols, hSt);

        BigDecimal granTotal = rows.stream().map(r -> toBD(r[1])).reduce(BigDecimal.ZERO, BigDecimal::add);
        CellStyle[] colores = {fondoStyle(wb, VERDE_OK), fondoStyle(wb, NARANJA),
                               fondoStyle(wb, GRIS_FILA)};

        int idx = 0;
        for (Object[] r : rows) {
            Row xr = sheet.createRow(row++);
            CellStyle cst = colores[idx % colores.length];
            BigDecimal total = toBD(r[1]);
            double pct = granTotal.compareTo(BigDecimal.ZERO) > 0
                    ? total.divide(granTotal, 4, RoundingMode.HALF_UP)
                           .multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;

            String servicio = str(r[0]);
            if (servicio.isBlank()) servicio = "(Sin especificar)";

            celda(xr, 0, servicio, cst);
            celdaNum(xr, 1, total.doubleValue(), mSt);
            celdaNum(xr, 2, r[2] != null ? ((Number) r[2]).doubleValue() : 0, null);
            celdaNum(xr, 3, bd(r[3]), mSt);
            celda(xr, 4, String.format("%.2f%%", pct), cst);
            idx++;
        }

        autoSize(sheet, cols.length);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HOJA 6: MOROSOS POR ESTRATO
    // ══════════════════════════════════════════════════════════════════════════

    private void hojaMorososPorEstrato(XSSFWorkbook wb,
                                        List<String> municipios, List<String> archivos) {
        List<Object[]> rows = facturaRepository.findMorososPorEstrato(municipios, archivos);

        Sheet sheet = wb.createSheet("Morosos por Estrato");
        CellStyle hSt = headerStyle(wb, AZUL_EPQ);
        CellStyle mSt = moedaStyle(wb);
        CellStyle rSt = fondoStyle(wb, ROJO_MOROSO);

        int row = 0;
        titulo(sheet, wb, "USUARIOS MOROSOS AGRUPADOS POR ESTRATO", 7, row++);
        row++;

        String[] cols = {"Estrato", "Código", "Nombre", "Municipio",
                         "Meses Mora", "Saldo Mora", "Total a Pagar"};
        encabezados(sheet, row++, cols, hSt);

        String estratoActual = "";
        int posEnEstrato = 1;
        Map<String, BigDecimal> totalPorEstrato = new LinkedHashMap<>();

        for (Object[] r : rows) {
            String estrato = r[0] != null ? "Estrato " + r[0] : "Sin estrato";

            if (!estrato.equals(estratoActual)) {
                // Separador de estrato
                if (!estratoActual.isEmpty()) row++;
                Row sep = sheet.createRow(row++);
                CellStyle sepSt = headerStyle(wb, new Color(189, 215, 238));
                celda(sep, 0, "── " + estrato + " ──", sepSt);
                estratoActual = estrato;
                posEnEstrato = 1;
            }

            Row xr = sheet.createRow(row++);
            CellStyle cst = posEnEstrato <= 5 ? rSt : null;

            celda(xr, 0, String.valueOf(posEnEstrato++), cst);
            celda(xr, 1, str(r[1]), cst);
            celda(xr, 2, str(r[2]), cst);
            celda(xr, 3, str(r[3]), cst);
            celdaNum(xr, 4, r[4] != null ? ((Number) r[4]).doubleValue() : 0, null);
            celdaNum(xr, 5, bd(r[5]), mSt);
            celdaNum(xr, 6, bd(r[6]), mSt);

            totalPorEstrato.merge(estrato, toBD(r[6]), BigDecimal::add);
        }

        // Resumen por estrato
        row += 2;
        titulo(sheet, wb, "RESUMEN POR ESTRATO", 3, row++);
        row++;
        encabezados(sheet, row++, new String[]{"Estrato", "Total Morosos", "Total a Pagar"},
                headerStyle(wb, AZUL_EPQ));
        for (Map.Entry<String, BigDecimal> e : totalPorEstrato.entrySet()) {
            Row xr = sheet.createRow(row++);
            celda(xr, 0, e.getKey(), null);
            celdaNum(xr, 2, e.getValue().doubleValue(), mSt);
        }

        autoSize(sheet, cols.length);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HOJA 7: PAGOS POR ESTRATO (comparativa 1-2-3)
    // ══════════════════════════════════════════════════════════════════════════

    private void hojaPagosPorEstrato(XSSFWorkbook wb, FiltroInformeDto filtros,
                                      List<String> municipios, List<String> archivos) {
        List<Object[]> rows = facturaRepository.findResumenPorEstrato(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios, archivos);

        Sheet sheet = wb.createSheet("Pagos por Estrato");
        CellStyle hSt  = headerStyle(wb, AZUL_EPQ);
        CellStyle mSt  = moedaStyle(wb);
        CellStyle e1St = fondoStyle(wb, new Color(198, 239, 206));  // verde E1
        CellStyle e2St = fondoStyle(wb, new Color(255, 235, 156));  // amarillo E2
        CellStyle e3St = fondoStyle(wb, new Color(189, 215, 238));  // azul E3
        CellStyle e4St = fondoStyle(wb, GRIS_FILA);

        int row = 0;
        titulo(sheet, wb, "PAGOS Y RECAUDO POR ESTRATO SOCIOECONÓMICO", 7, row++);
        row++;

        String[] cols = {"Estrato", "# Registros", "Total Recaudado",
                         "Total Mora", "Total Saldo Mora",
                         "% del Recaudo Total", "Promedio por Factura"};
        encabezados(sheet, row++, cols, hSt);

        BigDecimal granTotal = rows.stream().map(r -> toBD(r[1])).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumEstrato123 = BigDecimal.ZERO;

        Map<Integer, CellStyle> estilosPorEstrato = Map.of(
                1, e1St, 2, e2St, 3, e3St);

        for (Object[] r : rows) {
            Row xr = sheet.createRow(row++);
            int estrato  = r[0] != null ? ((Number) r[0]).intValue() : 0;
            long conteo  = r[4] != null ? ((Number) r[4]).longValue() : 1;
            BigDecimal total = toBD(r[1]);
            BigDecimal promedio = conteo > 0
                    ? total.divide(BigDecimal.valueOf(conteo), 0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            double pct = granTotal.compareTo(BigDecimal.ZERO) > 0
                    ? total.divide(granTotal, 4, RoundingMode.HALF_UP)
                           .multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;

            CellStyle cst = estilosPorEstrato.getOrDefault(estrato, e4St);
            celda(xr, 0, "Estrato " + estrato, cst);
            celdaNum(xr, 1, (double) conteo, null);
            celdaNum(xr, 2, total.doubleValue(), mSt);
            celdaNum(xr, 3, bd(r[2]), mSt);
            celdaNum(xr, 4, bd(r[3]), mSt);
            celda(xr, 5, String.format("%.2f%%", pct), cst);
            celdaNum(xr, 6, promedio.doubleValue(), mSt);

            if (estrato >= 1 && estrato <= 3) sumEstrato123 = sumEstrato123.add(total);
        }

        // Fila especial estratos 1-2-3
        row++;
        Row sum123 = sheet.createRow(row++);
        CellStyle boldSt = boldStyle(wb);
        celda(sum123, 0, "SUMA ESTRATOS 1+2+3", boldSt);
        celdaNum(sum123, 2, sumEstrato123.doubleValue(), mSt);
        double pct123 = granTotal.compareTo(BigDecimal.ZERO) > 0
                ? sumEstrato123.divide(granTotal, 4, RoundingMode.HALF_UP)
                               .multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;
        celda(sum123, 5, String.format("%.2f%% del total", pct123), boldSt);

        // Fila gran total
        Row totalRow = sheet.createRow(row);
        celda(totalRow, 0, "GRAN TOTAL", boldSt);
        celdaNum(totalRow, 2, granTotal.doubleValue(), mSt);
        celda(totalRow, 5, "100.00%", boldSt);

        autoSize(sheet, cols.length);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HOJA 8: RECAUDO DIARIO
    // ══════════════════════════════════════════════════════════════════════════

    private void hojaRecaudoDiario(XSSFWorkbook wb, FiltroInformeDto filtros,
                                    List<String> municipios, List<String> archivos) {
        List<Object[]> rows = facturaRepository.findRecaudoDiario(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios, archivos);

        Sheet sheet = wb.createSheet("Recaudo Diario");
        CellStyle hSt = headerStyle(wb, AZUL_EPQ);
        CellStyle mSt = moedaStyle(wb);
        CellStyle pSt = fondoStyle(wb, GRIS_FILA);

        int row = 0;
        titulo(sheet, wb, "RECAUDO DIARIO", 4, row++);
        row++;

        String[] cols = {"Fecha", "Total Recaudado", "# Facturas", "Promedio por Factura"};
        encabezados(sheet, row++, cols, hSt);

        BigDecimal granTotal = BigDecimal.ZERO;
        long granConteo = 0;
        int idx = 0;

        for (Object[] r : rows) {
            Row xr = sheet.createRow(row++);
            CellStyle cst = (idx++ % 2 == 0) ? null : pSt;
            long conteo = r[2] != null ? ((Number) r[2]).longValue() : 1;
            BigDecimal total = toBD(r[1]);
            BigDecimal prom = conteo > 0
                    ? total.divide(BigDecimal.valueOf(conteo), 0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            celda(xr, 0, r[0] != null ? r[0].toString() : "", cst);
            celdaNum(xr, 1, total.doubleValue(), mSt);
            celdaNum(xr, 2, (double) conteo, null);
            celdaNum(xr, 3, prom.doubleValue(), mSt);

            granTotal = granTotal.add(total);
            granConteo += conteo;
        }

        row++;
        Row totalRow = sheet.createRow(row);
        CellStyle boldSt = boldStyle(wb);
        celda(totalRow, 0, "TOTAL", boldSt);
        celdaNum(totalRow, 1, granTotal.doubleValue(), mSt);
        celdaNum(totalRow, 2, (double) granConteo, null);
        BigDecimal promGlobal = granConteo > 0
                ? granTotal.divide(BigDecimal.valueOf(granConteo), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        celdaNum(totalRow, 3, promGlobal.doubleValue(), mSt);

        autoSize(sheet, cols.length);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HOJA 9: PROYECCIÓN
    // ══════════════════════════════════════════════════════════════════════════

    private void hojaProyeccion(XSSFWorkbook wb, FiltroInformeDto filtros,
                                 List<String> municipios, List<String> archivos) {
        List<Object[]> rows = facturaRepository.findDatosProyeccion(municipios, archivos);

        Sheet sheet = wb.createSheet("Proyección");
        CellStyle hSt  = headerStyle(wb, AZUL_EPQ);
        CellStyle mSt  = moedaStyle(wb);
        CellStyle pSt  = fondoStyle(wb, GRIS_FILA);
        CellStyle projSt = fondoStyle(wb, new Color(180, 220, 255));

        int row = 0;
        titulo(sheet, wb, "PROMEDIO MENSUAL Y PROYECCIÓN DE RECAUDO", 4, row++);
        row++;

        // Tabla 1: datos históricos
        String[] colsHist = {"Período", "Total Recaudado", "# Facturas", "Promedio por Factura"};
        encabezados(sheet, row++, colsHist, hSt);

        List<BigDecimal> valores = new ArrayList<>();
        List<Long> conteos = new ArrayList<>();
        int idx = 0;

        for (Object[] r : rows) {
            Row xr = sheet.createRow(row++);
            CellStyle cst = (idx++ % 2 == 0) ? null : pSt;
            long conteo = r[2] != null ? ((Number) r[2]).longValue() : 1;
            BigDecimal total = toBD(r[1]);
            BigDecimal prom = conteo > 0
                    ? total.divide(BigDecimal.valueOf(conteo), 0, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            celda(xr, 0, str(r[0]), cst);
            celdaNum(xr, 1, total.doubleValue(), mSt);
            celdaNum(xr, 2, (double) conteo, null);
            celdaNum(xr, 3, prom.doubleValue(), mSt);

            valores.add(total);
            conteos.add(conteo);
        }

        if (valores.isEmpty()) return;

        // Calcular promedios
        BigDecimal sumTotal = valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal promedioMensual = sumTotal.divide(
                BigDecimal.valueOf(valores.size()), 0, RoundingMode.HALF_UP);
        long promedioFacturas = conteos.isEmpty() ? 0
                : conteos.stream().mapToLong(Long::longValue).sum() / conteos.size();

        // Tabla 2: proyección
        row += 2;
        titulo(sheet, wb, "PROYECCIÓN A " + filtros.getMesesProyeccion() + " MESES", 3, row++);
        row++;
        encabezados(sheet, row++,
                new String[]{"Mes Proyectado", "Recaudo Estimado", "Base de Cálculo"}, hSt);

        // Último mes conocido
        String ultimoMes = rows.isEmpty() ? "" : str(rows.get(rows.size() - 1)[0]);
        int[] añoMes = parsearMesRef(ultimoMes);

        for (int m = 1; m <= filtros.getMesesProyeccion(); m++) {
            añoMes = siguienteMes(añoMes);
            Row xr = sheet.createRow(row++);
            celda(xr, 0, añoMes[0] + "-" + String.format("%02d", añoMes[1]), projSt);
            celdaNum(xr, 1, promedioMensual.doubleValue(), mSt);
            celda(xr, 2, "Promedio histórico (" + valores.size() + " meses)", projSt);
        }

        // Resumen estadístico
        row += 2;
        CellStyle boldSt = boldStyle(wb);
        sheet.createRow(row++).createCell(0).setCellValue("ESTADÍSTICAS DEL PERÍODO ANALIZADO");
        filaDato(sheet, row++, "Períodos analizados:", String.valueOf(valores.size()), boldSt);
        filaDato(sheet, row++, "Promedio mensual:", "$" + FMT_MONEDA.format(promedioMensual), boldSt);
        filaDato(sheet, row++, "Recaudo total histórico:", "$" + FMT_MONEDA.format(sumTotal), boldSt);
        filaDato(sheet, row++, "Promedio facturas/mes:", String.valueOf(promedioFacturas), boldSt);
        BigDecimal maxVal = valores.stream().max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        BigDecimal minVal = valores.stream().min(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        filaDato(sheet, row++, "Mes de mayor recaudo:", "$" + FMT_MONEDA.format(maxVal), boldSt);
        filaDato(sheet, row,   "Mes de menor recaudo:", "$" + FMT_MONEDA.format(minVal), boldSt);

        autoSize(sheet, 4);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // SECCIONES PDF (versión compacta de cada informe)
    // ══════════════════════════════════════════════════════════════════════════

    private void agregarEncabezadoPdf(Document doc, FiltroInformeDto filtros) throws Exception {
        Font tf = new Font(Font.HELVETICA, 14, Font.BOLD, AZUL_EPQ);
        Paragraph t = new Paragraph("EPQ — INFORMES ANALÍTICOS", tf);
        t.setAlignment(Element.ALIGN_CENTER);
        doc.add(t);
        Font nf = new Font(Font.HELVETICA, 9);
        doc.add(new Paragraph("Fecha: " + LocalDate.now().format(FMT_FECHA), nf));
        if (filtros.getMunicipios() != null && !filtros.getMunicipios().isEmpty()) {
            doc.add(new Paragraph("Municipios: " + String.join(", ", filtros.getMunicipios()), nf));
        }
        doc.add(new Paragraph(" "));
    }

    private void pdfTopMorosos(Document doc, FiltroInformeDto filtros,
                                List<String> municipios, List<Integer> estratos,
                                List<String> archivos) throws Exception {
        tituloSeccionPdf(doc, "TOP " + filtros.getTopN() + " MOROSOS");
        List<Object[]> rows = facturaRepository.findTopMorosos(municipios, estratos, archivos);

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{5, 15, 20, 12, 10, 15});
        for (String h : new String[]{"#","Nombre","Municipio","E","Meses","Saldo Mora"}) hPdf(table, h);

        int pos = 1;
        for (Object[] r : rows) {
            if (pos > filtros.getTopN()) break;
            Color fondo = pos <= 10 ? ROJO_MOROSO : Color.WHITE;
            celdaPdf(table, String.valueOf(pos++), fondo);
            celdaPdf(table, str(r[1]), fondo);
            celdaPdf(table, str(r[2]), fondo);
            celdaPdf(table, r[3] != null ? "E" + r[3] : "-", fondo);
            celdaPdf(table, r[5] != null ? str(r[5]) : "-", fondo);
            celdaPdf(table, "$" + FMT_MONEDA.format(toBD(r[7])), fondo);
        }
        doc.add(table);
    }

    private void pdfRecaudoGeneral(Document doc, FiltroInformeDto filtros,
                                    List<String> municipios, List<String> archivos) throws Exception {
        tituloSeccionPdf(doc, "RECAUDO GENERAL");
        List<Object[]> rows = facturaRepository.findRecaudoGeneral(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios, archivos);

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{15, 20, 20, 10});
        for (String h : new String[]{"Período","Municipio","Total Recaudado","Facturas"}) hPdf(table, h);
        for (Object[] r : rows) {
            celdaPdf(table, str(r[0]), null);
            celdaPdf(table, str(r[1]), null);
            celdaPdf(table, "$" + FMT_MONEDA.format(toBD(r[3])), null);
            celdaPdf(table, str(r[4]), null);
        }
        doc.add(table);
    }

    private void pdfEdadMora(Document doc, List<String> municipios, List<String> archivos)
            throws Exception {
        tituloSeccionPdf(doc, "EDAD DE LOS MOROSOS");
        List<Object[]> rows = facturaRepository.findDistribucionEdadMora(municipios, archivos);

        // Agrupar en rangos
        long total = rows.stream().mapToLong(r -> r[1] != null ? ((Number)r[1]).longValue() : 0).sum();
        Map<String,Long> rangos = new LinkedHashMap<>();
        rangos.put("1-3 meses", 0L);
        rangos.put("4-6 meses", 0L);
        rangos.put("7-12 meses", 0L);
        rangos.put("13-24 meses", 0L);
        rangos.put("25+ meses", 0L);

        for (Object[] r : rows) {
            int m = r[0] != null ? ((Number)r[0]).intValue() : 0;
            long cnt = r[1] != null ? ((Number)r[1]).longValue() : 0;
            String key = m <= 3 ? "1-3 meses" : m <= 6 ? "4-6 meses" :
                         m <= 12 ? "7-12 meses" : m <= 24 ? "13-24 meses" : "25+ meses";
            rangos.merge(key, cnt, Long::sum);
        }

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(80);
        table.setWidths(new float[]{30, 20, 20});
        for (String h : new String[]{"Rango de Mora","Cantidad","% del Total"}) hPdf(table, h);
        for (Map.Entry<String,Long> e : rangos.entrySet()) {
            if (e.getValue() == 0) continue;
            double pct = total > 0 ? e.getValue() * 100.0 / total : 0;
            celdaPdf(table, e.getKey(), null);
            celdaPdf(table, String.valueOf(e.getValue()), null);
            celdaPdf(table, String.format("%.1f%%", pct), null);
        }
        doc.add(table);
    }

    private void pdfRecaudoMunicipio(Document doc, FiltroInformeDto filtros,
                                      List<String> municipios, List<String> archivos)
            throws Exception {
        tituloSeccionPdf(doc, "RECAUDO POR MUNICIPIO");
        List<Object[]> rows = facturaRepository.sumTotalPorMunicipio(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{30, 25, 25});
        for (String h : new String[]{"Municipio","Total Recaudado","Total Mora"}) hPdf(table, h);
        for (Object[] r : rows) {
            celdaPdf(table, str(r[0]), null);
            celdaPdf(table, "$" + FMT_MONEDA.format(toBD(r[1])), null);
            celdaPdf(table, "$" + FMT_MONEDA.format(toBD(r[2])), null);
        }
        doc.add(table);
    }

    private void pdfRecaudoServicio(Document doc, FiltroInformeDto filtros,
                                     List<String> municipios, List<String> archivos)
            throws Exception {
        tituloSeccionPdf(doc, "RECAUDO POR SERVICIO");
        List<Object[]> rows = facturaRepository.findRecaudoPorServicio(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios, archivos);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{35, 25, 20});
        for (String h : new String[]{"Servicio","Total Recaudado","# Facturas"}) hPdf(table, h);
        for (Object[] r : rows) {
            String s = str(r[0]);
            celdaPdf(table, s.isBlank() ? "(Sin especificar)" : s, null);
            celdaPdf(table, "$" + FMT_MONEDA.format(toBD(r[1])), null);
            celdaPdf(table, str(r[2]), null);
        }
        doc.add(table);
    }

    private void pdfMorososPorEstrato(Document doc, List<String> municipios,
                                       List<String> archivos) throws Exception {
        tituloSeccionPdf(doc, "MOROSOS POR ESTRATO");
        List<Object[]> rows = facturaRepository.findMorososPorEstrato(municipios, archivos);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{8, 20, 15, 10, 20});
        for (String h : new String[]{"E","Nombre","Municipio","Meses","Saldo Mora"}) hPdf(table, h);
        String estratoActual = "";
        int pos = 1;
        for (Object[] r : rows) {
            String est = r[0] != null ? "E" + r[0] : "-";
            if (!est.equals(estratoActual)) { estratoActual = est; pos = 1; }
            if (pos > 10) continue;   // máximo 10 por estrato en PDF
            Color c = pos <= 3 ? ROJO_MOROSO : null;
            celdaPdf(table, est, c);
            celdaPdf(table, str(r[2]), c);
            celdaPdf(table, str(r[3]), c);
            celdaPdf(table, r[4] != null ? str(r[4]) : "-", c);
            celdaPdf(table, "$" + FMT_MONEDA.format(toBD(r[5])), c);
            pos++;
        }
        doc.add(table);
    }

    private void pdfPagosPorEstrato(Document doc, FiltroInformeDto filtros,
                                     List<String> municipios, List<String> archivos)
            throws Exception {
        tituloSeccionPdf(doc, "PAGOS POR ESTRATO (1-2-3)");
        List<Object[]> rows = facturaRepository.findResumenPorEstrato(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios, archivos);

        BigDecimal granTotal = rows.stream().map(r -> toBD(r[1])).reduce(BigDecimal.ZERO, BigDecimal::add);
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{15, 25, 20, 15});
        for (String h : new String[]{"Estrato","Total Recaudado","Total Mora","% Total"}) hPdf(table, h);

        BigDecimal sum123 = BigDecimal.ZERO;
        for (Object[] r : rows) {
            int est = r[0] != null ? ((Number)r[0]).intValue() : 0;
            BigDecimal total = toBD(r[1]);
            double pct = granTotal.compareTo(BigDecimal.ZERO) > 0
                    ? total.divide(granTotal, 4, RoundingMode.HALF_UP)
                           .multiply(BigDecimal.valueOf(100)).doubleValue() : 0;
            Color c = est <= 3 ? VERDE_OK : null;
            celdaPdf(table, "Estrato " + est, c);
            celdaPdf(table, "$" + FMT_MONEDA.format(total), c);
            celdaPdf(table, "$" + FMT_MONEDA.format(toBD(r[2])), c);
            celdaPdf(table, String.format("%.1f%%", pct), c);
            if (est >= 1 && est <= 3) sum123 = sum123.add(total);
        }
        Font bf = new Font(Font.HELVETICA, 9, Font.BOLD);
        doc.add(table);
        doc.add(new Paragraph("Suma estratos 1+2+3: $" + FMT_MONEDA.format(sum123), bf));
    }

    private void pdfRecaudoDiario(Document doc, FiltroInformeDto filtros,
                                   List<String> municipios, List<String> archivos)
            throws Exception {
        tituloSeccionPdf(doc, "RECAUDO DIARIO");
        List<Object[]> rows = facturaRepository.findRecaudoDiario(
                filtros.getFechaInicio(), filtros.getFechaFin(), municipios, archivos);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(80);
        table.setWidths(new float[]{25, 30, 20});
        for (String h : new String[]{"Fecha","Total Recaudado","# Facturas"}) hPdf(table, h);
        for (Object[] r : rows) {
            celdaPdf(table, r[0] != null ? r[0].toString() : "", null);
            celdaPdf(table, "$" + FMT_MONEDA.format(toBD(r[1])), null);
            celdaPdf(table, str(r[2]), null);
        }
        doc.add(table);
    }

    private void pdfProyeccion(Document doc, FiltroInformeDto filtros,
                                List<String> municipios, List<String> archivos)
            throws Exception {
        tituloSeccionPdf(doc, "PROMEDIO Y PROYECCIÓN DE RECAUDO");
        List<Object[]> rows = facturaRepository.findDatosProyeccion(municipios, archivos);

        List<BigDecimal> vals = rows.stream().map(r -> toBD(r[1])).collect(Collectors.toList());
        if (vals.isEmpty()) { doc.add(new Paragraph("Sin datos")); return; }

        BigDecimal sum = vals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal prom = sum.divide(BigDecimal.valueOf(vals.size()), 0, RoundingMode.HALF_UP);

        Font bf = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font nf = new Font(Font.HELVETICA, 9);
        doc.add(new Paragraph("Períodos analizados: " + vals.size(), nf));
        doc.add(new Paragraph("Promedio mensual: $" + FMT_MONEDA.format(prom), bf));
        doc.add(new Paragraph("Recaudo total histórico: $" + FMT_MONEDA.format(sum), nf));
        doc.add(new Paragraph(" "));

        String ultimoMes = rows.isEmpty() ? "" : str(rows.get(rows.size()-1)[0]);
        int[] am = parsearMesRef(ultimoMes);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(60);
        table.setWidths(new float[]{25, 30});
        hPdf(table, "Mes Proyectado"); hPdf(table, "Recaudo Estimado");
        for (int m = 1; m <= filtros.getMesesProyeccion(); m++) {
            am = siguienteMes(am);
            celdaPdf(table, am[0] + "-" + String.format("%02d", am[1]),
                     new Color(180, 220, 255));
            celdaPdf(table, "$" + FMT_MONEDA.format(prom),
                     new Color(180, 220, 255));
        }
        doc.add(table);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS EXCEL
    // ══════════════════════════════════════════════════════════════════════════

    private CellStyle headerStyle(XSSFWorkbook wb, Color color) {
        CellStyle st = wb.createCellStyle();
        org.apache.poi.xssf.usermodel.XSSFFont f =
                (org.apache.poi.xssf.usermodel.XSSFFont) wb.createFont();
        f.setBold(true);
        f.setColor(new XSSFColor(Color.WHITE, null));
        st.setFont(f);
        st.setFillForegroundColor(new XSSFColor(color, null));
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        st.setAlignment(HorizontalAlignment.CENTER);
        st.setBorderBottom(BorderStyle.THIN);
        st.setBorderTop(BorderStyle.THIN);
        st.setBorderLeft(BorderStyle.THIN);
        st.setBorderRight(BorderStyle.THIN);
        return st;
    }

    private CellStyle fondoStyle(XSSFWorkbook wb, Color color) {
        CellStyle st = wb.createCellStyle();
        st.setFillForegroundColor(new XSSFColor(color, null));
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return st;
    }

    private CellStyle moedaStyle(XSSFWorkbook wb) {
        CellStyle st = wb.createCellStyle();
        st.setDataFormat(wb.createDataFormat().getFormat("$ #,##0"));
        return st;
    }

    private CellStyle boldStyle(XSSFWorkbook wb) {
        CellStyle st = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font f = wb.createFont();
        f.setBold(true);
        st.setFont(f);
        return st;
    }

    private void titulo(Sheet sheet, XSSFWorkbook wb,
                        String texto, int cols, int rowNum) {
        Row r = sheet.createRow(rowNum);
        Cell c = r.createCell(0);
        c.setCellValue(texto);
        CellStyle st = wb.createCellStyle();
        org.apache.poi.xssf.usermodel.XSSFFont f =
                (org.apache.poi.xssf.usermodel.XSSFFont) wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 12);
        f.setColor(new XSSFColor(AZUL_EPQ, null));
        st.setFont(f);
        c.setCellStyle(st);
        if (cols > 1) sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, cols - 1));
    }

    private void encabezados(Sheet sheet, int rowNum, String[] cols, CellStyle st) {
        Row r = sheet.createRow(rowNum);
        for (int i = 0; i < cols.length; i++) {
            Cell c = r.createCell(i);
            c.setCellValue(cols[i]);
            c.setCellStyle(st);
        }
    }

    private void celda(Row row, int col, String val, CellStyle st) {
        Cell c = row.createCell(col);
        c.setCellValue(val != null ? val : "");
        if (st != null) c.setCellStyle(st);
    }

    private void celdaNum(Row row, int col, double val, CellStyle st) {
        Cell c = row.createCell(col);
        c.setCellValue(val);
        if (st != null) c.setCellStyle(st);
    }

    private void filaDato(Sheet sheet, int rowNum, String etiq, String val, CellStyle st) {
        Row r = sheet.createRow(rowNum);
        r.createCell(0).setCellValue(etiq);
        Cell cv = r.createCell(1);
        cv.setCellValue(val);
        if (st != null) cv.setCellStyle(st);
    }

    private void autoSize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            try { sheet.autoSizeColumn(i); } catch (Exception ignored) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // HELPERS PDF
    // ══════════════════════════════════════════════════════════════════════════

    private void tituloSeccionPdf(Document doc, String texto) throws Exception {
        Font f = new Font(Font.HELVETICA, 11, Font.BOLD, AZUL_EPQ);
        Paragraph p = new Paragraph(texto, f);
        p.setSpacingBefore(10); p.setSpacingAfter(4);
        doc.add(p);
    }

    private void hPdf(PdfPTable table, String texto) {
        Font f = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
        PdfPCell cell = new PdfPCell(new Phrase(texto, f));
        cell.setBackgroundColor(AZUL_EPQ);
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void celdaPdf(PdfPTable table, String texto, Color fondo) {
        Font f = new Font(Font.HELVETICA, 8);
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", f));
        if (fondo != null) cell.setBackgroundColor(fondo);
        cell.setPadding(3);
        table.addCell(cell);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // UTILIDADES
    // ══════════════════════════════════════════════════════════════════════════

    private List<String> normalizarMunicipios(List<String> lista) {
        if (lista == null || lista.isEmpty()) return null;
        return lista.stream()
                .filter(m -> m != null && !m.isBlank())
                .map(MunicipioConstantes::eliminarTildes)
                .map(String::toUpperCase)
                .distinct()
                .collect(Collectors.toList());
    }

    private static String str(Object o) {
        return o != null ? o.toString().trim() : "";
    }

    private static double bd(Object o) {
        return toBD(o).doubleValue();
    }

    private static BigDecimal toBD(Object o) {
        if (o == null) return BigDecimal.ZERO;
        if (o instanceof BigDecimal) return (BigDecimal) o;
        if (o instanceof Number) return BigDecimal.valueOf(((Number) o).doubleValue());
        try { return new BigDecimal(o.toString()); }
        catch (Exception e) { return BigDecimal.ZERO; }
    }

    /** Parsea "2024-03" → {2024, 3}. Si falla, usa mes actual. */
    private static int[] parsearMesRef(String mesRef) {
        if (mesRef == null || !mesRef.matches("\\d{4}-\\d{2}")) {
            return new int[]{LocalDate.now().getYear(), LocalDate.now().getMonthValue()};
        }
        String[] p = mesRef.split("-");
        return new int[]{Integer.parseInt(p[0]), Integer.parseInt(p[1])};
    }

    /** Incrementa en un mes. */
    private static int[] siguienteMes(int[] am) {
        int mes = am[1] + 1;
        int año = am[0];
        if (mes > 12) { mes = 1; año++; }
        return new int[]{año, mes};
    }
}
