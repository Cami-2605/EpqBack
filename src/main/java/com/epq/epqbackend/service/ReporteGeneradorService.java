package com.epq.epqbackend.service;

import com.epq.epqbackend.dto.FiltroReporteDto;
import com.epq.epqbackend.exception.EpqException;
import com.epq.epqbackend.exception.ErrorCode;
import com.epq.epqbackend.model.Factura;

// OpenPDF — imports específicos para evitar colisión con POI
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

// Apache POI — imports específicos para evitar colisión con OpenPDF
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Genera archivos Excel y PDF para los 6 tipos de reporte del sistema EPQ.
 *
 * Tipos de reporte soportados:
 *   1. AGRUPADO_MES         - Totales agrupados por período (YYYY-MM).
 *   2. TOTALES_MUNICIPIO    - Totales por municipio.
 *   3. TOTALES_ESTRATO      - Totales por estrato socioeconómico.
 *   4. DEUDORES_MOROSOS     - Registros con mora o estado VENCIDO.
 *   5. DETALLE_VIVIENDA     - Detalle por dirección/barrio/contrato.
 *   6. COMBINADO            - Todas las hojas anteriores en un solo archivo.
 *
 * La firma generarReporte() acepta un FiltroReporteDto con la lista tiposReporte.
 * Si tiposReporte está vacío o nulo, genera el reporte completo (COMBINADO).
 */
@Service
public class ReporteGeneradorService {

    @Autowired
    private FacturaService facturaService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final NumberFormat CURRENCY_FORMAT;
    static {
        CURRENCY_FORMAT = NumberFormat.getNumberInstance(new Locale("es", "CO"));
        CURRENCY_FORMAT.setMinimumFractionDigits(0);
        CURRENCY_FORMAT.setMaximumFractionDigits(2);
    }

    // ── Colores EPQ ────────────────────────────────────────────────────────────
    private static final Color COLOR_ENCABEZADO  = new Color(0, 84, 166);   // azul EPQ
    private static final Color COLOR_SUBENCABEZADO = new Color(189, 215, 238);
    private static final Color COLOR_MOROSO      = new Color(255, 199, 206); // rojo claro
    private static final Color COLOR_FILA_PAR    = new Color(242, 242, 242);

    // ══════════════════════════════════════════════════════════════════════════
    // API principal
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Genera el reporte en Excel (.xlsx).
     *
     * @param filtros filtros y opciones del reporte
     * @return bytes del archivo Excel
     */
    public byte[] generarExcel(FiltroReporteDto filtros) {
        List<Factura> facturas = facturaService.consultarConFiltros(filtros);
        validarDatos(facturas);
        Map<String, Object> resumen = facturaService.calcularResumen(facturas, filtros);

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            List<String> tipos = resolverTipos(filtros.getTiposReporte());

            for (String tipo : tipos) {
                switch (tipo) {
                    case "AGRUPADO_MES"       -> crearHojaMes(wb, facturas, resumen);
                    case "TOTALES_MUNICIPIO"  -> crearHojaMunicipio(wb, facturas);
                    case "TOTALES_ESTRATO"    -> crearHojaEstrato(wb, facturas);
                    case "DEUDORES_MOROSOS"   -> crearHojaMorosos(wb, facturas);
                    case "DETALLE_VIVIENDA"   -> crearHojaDetalle(wb, facturas);
                    default                  -> crearHojaDatos(wb, facturas);
                }
            }

            // Siempre agrega la hoja de resumen
            crearHojaResumen(wb, resumen, filtros);

            wb.write(out);
            return out.toByteArray();

        } catch (EpqException e) {
            throw e;
        } catch (Exception e) {
            throw new EpqException(ErrorCode.REPORTE_ERROR_GENERACION,
                    "Error generando el Excel: " + e.getMessage(), e);
        }
    }

    /**
     * Genera el reporte en PDF.
     */
    public byte[] generarPdf(FiltroReporteDto filtros) {
        List<Factura> facturas = facturaService.consultarConFiltros(filtros);
        validarDatos(facturas);
        Map<String, Object> resumen = facturaService.calcularResumen(facturas, filtros);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4.rotate(), 20, 20, 40, 30);
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.open();

            // Encabezado general
            agregarEncabezadoPdf(doc, filtros);

            List<String> tipos = resolverTipos(filtros.getTiposReporte());

            for (String tipo : tipos) {
                switch (tipo) {
                    case "AGRUPADO_MES"       -> agregarSeccionMesPdf(doc, facturas);
                    case "TOTALES_MUNICIPIO"  -> agregarSeccionMunicipioPdf(doc, facturas);
                    case "TOTALES_ESTRATO"    -> agregarSeccionEstratoPdf(doc, facturas);
                    case "DEUDORES_MOROSOS"   -> agregarSeccionMorososPdf(doc, facturas);
                    case "DETALLE_VIVIENDA"   -> agregarSeccionDetallePdf(doc, facturas);
                    default                  -> agregarSeccionDatosPdf(doc, facturas);
                }
                doc.add(new Paragraph(" "));
            }

            // Resumen al final
            agregarResumenPdf(doc, resumen);

            doc.close();
            return out.toByteArray();

        } catch (EpqException e) {
            throw e;
        } catch (Exception e) {
            throw new EpqException(ErrorCode.REPORTE_ERROR_GENERACION,
                    "Error generando el PDF: " + e.getMessage(), e);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Hojas Excel
    // ══════════════════════════════════════════════════════════════════════════

    // ── Hoja 1: Reporte agrupado por mes ──────────────────────────────────────

    private void crearHojaMes(XSSFWorkbook wb, List<Factura> facturas,
                               Map<String, Object> resumen) {
        Sheet sheet = wb.createSheet("Reporte por Mes");
        CellStyle headerSt = estiloEncabezado(wb);
        CellStyle moneySt  = estiloMoneda(wb);
        CellStyle boldSt   = estiloBold(wb);

        // Agrupar por mes
        Map<String, List<Factura>> porMes = facturas.stream()
                .filter(f -> f.getMesReferencia() != null)
                .collect(Collectors.groupingBy(Factura::getMesReferencia,
                        TreeMap::new, Collectors.toList()));

        String[] cols = {"Período", "Cantidad Facturas", "Total Factura",
                         "Total Mora", "Saldo Mora", "Total a Pagar"};
        int row = 0;

        crearFilaTitulo(sheet, wb, "REPORTE AGRUPADO POR PERÍODO", cols.length, row++);
        row++;

        Row hRow = sheet.createRow(row++);
        for (int i = 0; i < cols.length; i++) crearCelda(hRow, i, cols[i], headerSt);

        BigDecimal sumTotal = BigDecimal.ZERO, sumMora = BigDecimal.ZERO, sumSaldo = BigDecimal.ZERO;
        int sumCantidad = 0;

        for (Map.Entry<String, List<Factura>> e : porMes.entrySet()) {
            Row r = sheet.createRow(row++);
            List<Factura> grupo = e.getValue();

            BigDecimal totalPagar   = sumar(grupo, Factura::getTotalPagar);
            BigDecimal totalMora    = sumar(grupo, Factura::getValorMora);
            BigDecimal totalSaldo   = sumar(grupo, Factura::getSaldoMora);
            BigDecimal totalFactura = sumar(grupo, Factura::getValorFactura);

            crearCelda(r, 0, e.getKey(), null);
            crearCeldaNum(r, 1, (double) grupo.size(), null);
            crearCeldaNum(r, 2, totalFactura.doubleValue(), moneySt);
            crearCeldaNum(r, 3, totalMora.doubleValue(), moneySt);
            crearCeldaNum(r, 4, totalSaldo.doubleValue(), moneySt);
            crearCeldaNum(r, 5, totalPagar.doubleValue(), moneySt);

            sumTotal    = sumTotal.add(totalPagar);
            sumMora     = sumMora.add(totalMora);
            sumSaldo    = sumSaldo.add(totalSaldo);
            sumCantidad += grupo.size();
        }

        // Fila totales
        Row totalRow = sheet.createRow(row);
        crearCelda(totalRow, 0, "TOTAL", boldSt);
        crearCeldaNum(totalRow, 1, (double) sumCantidad, boldSt);
        crearCeldaNum(totalRow, 2, 0, null); // valor factura global no se suma aquí
        crearCeldaNum(totalRow, 3, sumMora.doubleValue(), moneySt);
        crearCeldaNum(totalRow, 4, sumSaldo.doubleValue(), moneySt);
        crearCeldaNum(totalRow, 5, sumTotal.doubleValue(), moneySt);

        autoSize(sheet, cols.length);
    }

    // ── Hoja 2: Totales por municipio ─────────────────────────────────────────

    private void crearHojaMunicipio(XSSFWorkbook wb, List<Factura> facturas) {
        Sheet sheet = wb.createSheet("Totales por Municipio");
        CellStyle headerSt = estiloEncabezado(wb);
        CellStyle moneySt  = estiloMoneda(wb);
        CellStyle boldSt   = estiloBold(wb);

        Map<String, List<Factura>> porMunicipio = facturas.stream()
                .collect(Collectors.groupingBy(Factura::getMunicipio,
                        TreeMap::new, Collectors.toList()));

        String[] cols = {"Municipio", "Cantidad", "Total Factura", "Total Mora",
                         "Saldo Mora", "Total a Pagar", "Morosos"};
        int row = 0;
        crearFilaTitulo(sheet, wb, "TOTALES POR MUNICIPIO", cols.length, row++);
        row++;

        Row hRow = sheet.createRow(row++);
        for (int i = 0; i < cols.length; i++) crearCelda(hRow, i, cols[i], headerSt);

        BigDecimal gtTotal = BigDecimal.ZERO;

        for (Map.Entry<String, List<Factura>> e : porMunicipio.entrySet()) {
            Row r = sheet.createRow(row++);
            List<Factura> grupo = e.getValue();

            BigDecimal totalPagar   = sumar(grupo, Factura::getTotalPagar);
            BigDecimal totalMora    = sumar(grupo, Factura::getValorMora);
            BigDecimal totalSaldo   = sumar(grupo, Factura::getSaldoMora);
            BigDecimal totalFactura = sumar(grupo, Factura::getValorFactura);
            long morosos = grupo.stream().filter(Factura::tieneMora).count();

            crearCelda(r, 0, e.getKey(), null);
            crearCeldaNum(r, 1, (double) grupo.size(), null);
            crearCeldaNum(r, 2, totalFactura.doubleValue(), moneySt);
            crearCeldaNum(r, 3, totalMora.doubleValue(), moneySt);
            crearCeldaNum(r, 4, totalSaldo.doubleValue(), moneySt);
            crearCeldaNum(r, 5, totalPagar.doubleValue(), moneySt);
            crearCeldaNum(r, 6, (double) morosos, null);

            gtTotal = gtTotal.add(totalPagar);
        }

        Row totalRow = sheet.createRow(row);
        crearCelda(totalRow, 0, "GRAN TOTAL", boldSt);
        crearCeldaNum(totalRow, 1, (double) facturas.size(), boldSt);
        crearCeldaNum(totalRow, 5, gtTotal.doubleValue(), moneySt);

        autoSize(sheet, cols.length);
    }

    // ── Hoja 3: Totales por estrato ───────────────────────────────────────────

    private void crearHojaEstrato(XSSFWorkbook wb, List<Factura> facturas) {
        Sheet sheet = wb.createSheet("Totales por Estrato");
        CellStyle headerSt = estiloEncabezado(wb);
        CellStyle moneySt  = estiloMoneda(wb);
        CellStyle boldSt   = estiloBold(wb);

        Map<Integer, List<Factura>> porEstrato = facturas.stream()
                .filter(f -> f.getEstrato() != null)
                .collect(Collectors.groupingBy(Factura::getEstrato,
                        TreeMap::new, Collectors.toList()));

        String[] cols = {"Estrato", "Cantidad", "Total Factura", "Total Mora",
                         "Saldo Mora", "Total a Pagar", "% del Total"};
        int row = 0;
        crearFilaTitulo(sheet, wb, "TOTALES POR ESTRATO SOCIOECONÓMICO", cols.length, row++);
        row++;

        Row hRow = sheet.createRow(row++);
        for (int i = 0; i < cols.length; i++) crearCelda(hRow, i, cols[i], headerSt);

        BigDecimal granTotal = sumar(facturas, Factura::getTotalPagar);

        for (Map.Entry<Integer, List<Factura>> e : porEstrato.entrySet()) {
            Row r = sheet.createRow(row++);
            List<Factura> grupo = e.getValue();

            BigDecimal totalPagar   = sumar(grupo, Factura::getTotalPagar);
            BigDecimal totalMora    = sumar(grupo, Factura::getValorMora);
            BigDecimal totalSaldo   = sumar(grupo, Factura::getSaldoMora);
            BigDecimal totalFactura = sumar(grupo, Factura::getValorFactura);
            double porcentaje = granTotal.compareTo(BigDecimal.ZERO) > 0
                    ? totalPagar.divide(granTotal, 4, java.math.RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)).doubleValue()
                    : 0.0;

            crearCelda(r, 0, "Estrato " + e.getKey(), null);
            crearCeldaNum(r, 1, (double) grupo.size(), null);
            crearCeldaNum(r, 2, totalFactura.doubleValue(), moneySt);
            crearCeldaNum(r, 3, totalMora.doubleValue(), moneySt);
            crearCeldaNum(r, 4, totalSaldo.doubleValue(), moneySt);
            crearCeldaNum(r, 5, totalPagar.doubleValue(), moneySt);
            crearCelda(r, 6, String.format("%.2f%%", porcentaje), null);
        }

        autoSize(sheet, cols.length);
    }

    // ── Hoja 4: Deudores morosos ──────────────────────────────────────────────

    private void crearHojaMorosos(XSSFWorkbook wb, List<Factura> facturas) {
        Sheet sheet = wb.createSheet("Deudores Morosos");
        CellStyle headerSt = estiloEncabezado(wb);
        CellStyle moneySt  = estiloMoneda(wb);
        CellStyle morosoSt = estiloFondoColor(wb, COLOR_MOROSO);

        List<Factura> morosos = facturas.stream()
                .filter(Factura::tieneMora)
                .sorted(Comparator.comparing(Factura::getMunicipio,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        String[] cols = {"Municipio", "Estrato", "Detalle Vivienda", "Barrio",
                         "N° Contrato", "Período", "Valor Factura",
                         "Valor Mora", "Saldo Mora", "Total a Pagar", "Estado"};
        int row = 0;
        crearFilaTitulo(sheet, wb,
                "DEUDORES MOROSOS (" + morosos.size() + " registros)", cols.length, row++);
        row++;

        Row hRow = sheet.createRow(row++);
        for (int i = 0; i < cols.length; i++) crearCelda(hRow, i, cols[i], headerSt);

        for (Factura f : morosos) {
            Row r = sheet.createRow(row++);
            crearCelda(r, 0,  f.getMunicipio(), morosoSt);
            crearCelda(r, 1,  f.getEstrato() != null ? "Estrato " + f.getEstrato() : "N/A", null);
            crearCelda(r, 2,  nvl(f.getDireccionServicio()), null);
            crearCelda(r, 3,  nvl(f.getBarrio()), null);
            crearCelda(r, 4,  nvl(f.getNumeroContrato()), null);
            crearCelda(r, 5,  nvl(f.getMesReferencia()), null);
            crearCeldaNum(r, 6,  bd(f.getValorFactura()), moneySt);
            crearCeldaNum(r, 7,  bd(f.getValorMora()), moneySt);
            crearCeldaNum(r, 8,  bd(f.getSaldoMora()), moneySt);
            crearCeldaNum(r, 9,  bd(f.getTotalPagar()), moneySt);
            crearCelda(r, 10, nvl(f.getEstadoPago()), null);
        }

        autoSize(sheet, cols.length);
    }

    // ── Hoja 5: Detalle de vivienda ───────────────────────────────────────────

    private void crearHojaDetalle(XSSFWorkbook wb, List<Factura> facturas) {
        Sheet sheet = wb.createSheet("Detalle Vivienda");
        CellStyle headerSt = estiloEncabezado(wb);
        CellStyle moneySt  = estiloMoneda(wb);
        CellStyle parSt    = estiloFondoColor(wb, COLOR_FILA_PAR);

        String[] cols = {"Municipio", "Estrato", "Dirección", "Barrio",
                         "N° Contrato", "Servicio", "Período",
                         "Valor Factura", "Valor Mora", "Saldo Mora",
                         "Total a Pagar", "Estado"};
        int row = 0;
        crearFilaTitulo(sheet, wb, "DETALLE DE VIVIENDA", cols.length, row++);
        row++;

        Row hRow = sheet.createRow(row++);
        for (int i = 0; i < cols.length; i++) crearCelda(hRow, i, cols[i], headerSt);

        int idx = 0;
        for (Factura f : facturas) {
            Row r = sheet.createRow(row++);
            CellStyle rowSt = (idx++ % 2 == 0) ? null : parSt;

            crearCelda(r, 0,  f.getMunicipio(), rowSt);
            crearCelda(r, 1,  f.getEstrato() != null ? String.valueOf(f.getEstrato()) : "N/A", rowSt);
            crearCelda(r, 2,  nvl(f.getDireccionServicio()), rowSt);
            crearCelda(r, 3,  nvl(f.getBarrio()), rowSt);
            crearCelda(r, 4,  nvl(f.getNumeroContrato()), rowSt);
            crearCelda(r, 5,  nvl(f.getNombreServicio()), rowSt);
            crearCelda(r, 6,  nvl(f.getMesReferencia()), rowSt);
            crearCeldaNum(r, 7,  bd(f.getValorFactura()), moneySt);
            crearCeldaNum(r, 8,  bd(f.getValorMora()), moneySt);
            crearCeldaNum(r, 9,  bd(f.getSaldoMora()), moneySt);
            crearCeldaNum(r, 10, bd(f.getTotalPagar()), moneySt);
            crearCelda(r, 11, nvl(f.getEstadoPago()), rowSt);
        }

        autoSize(sheet, cols.length);
    }

    // ── Hoja 6: Datos crudos ──────────────────────────────────────────────────

    private void crearHojaDatos(XSSFWorkbook wb, List<Factura> facturas) {
        Sheet sheet = wb.createSheet("Datos");
        CellStyle headerSt = estiloEncabezado(wb);
        CellStyle moneySt  = estiloMoneda(wb);

        String[] cols = {"Municipio", "Estrato", "Período", "Fecha Factura",
                         "Valor Factura", "Valor Mora", "Saldo Mora",
                         "Total a Pagar", "Estado", "Dirección", "Barrio", "Contrato"};
        int row = 0;
        Row hRow = sheet.createRow(row++);
        for (int i = 0; i < cols.length; i++) crearCelda(hRow, i, cols[i], headerSt);

        for (Factura f : facturas) {
            Row r = sheet.createRow(row++);
            crearCelda(r, 0,  f.getMunicipio(), null);
            crearCelda(r, 1,  f.getEstrato() != null ? String.valueOf(f.getEstrato()) : "N/A", null);
            crearCelda(r, 2,  nvl(f.getMesReferencia()), null);
            crearCelda(r, 3,  f.getFechaFactura() != null ? f.getFechaFactura().format(DATE_FORMATTER) : "", null);
            crearCeldaNum(r, 4,  bd(f.getValorFactura()), moneySt);
            crearCeldaNum(r, 5,  bd(f.getValorMora()), moneySt);
            crearCeldaNum(r, 6,  bd(f.getSaldoMora()), moneySt);
            crearCeldaNum(r, 7,  bd(f.getTotalPagar()), moneySt);
            crearCelda(r, 8,  nvl(f.getEstadoPago()), null);
            crearCelda(r, 9,  nvl(f.getDireccionServicio()), null);
            crearCelda(r, 10, nvl(f.getBarrio()), null);
            crearCelda(r, 11, nvl(f.getNumeroContrato()), null);
        }

        autoSize(sheet, cols.length);
    }

    // ── Hoja de resumen ───────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private void crearHojaResumen(XSSFWorkbook wb, Map<String, Object> resumen,
                                   FiltroReporteDto filtros) {
        Sheet sheet = wb.createSheet("Resumen");
        CellStyle boldSt   = estiloBold(wb);
        CellStyle headerSt = estiloEncabezado(wb);
        CellStyle moneySt  = estiloMoneda(wb);

        int row = 0;
        crearFilaTitulo(sheet, wb, "RESUMEN GENERAL - EPQ", 3, row++);
        row++;

        crearFilaResumen(sheet, row++, "Fecha generación",
                LocalDate.now().format(DATE_FORMATTER), boldSt, null);
        crearFilaResumen(sheet, row++, "Total registros",
                String.valueOf(resumen.get("totalFacturas")), boldSt, null);
        crearFilaResumen(sheet, row++, "Total morosos",
                String.valueOf(resumen.get("totalMorosos")), boldSt, null);
        row++;

        crearFilaResumen(sheet, row++, "Total General",
                formatMoney((BigDecimal) resumen.get("totalGeneral")), boldSt, moneySt);
        crearFilaResumen(sheet, row++, "Total Mora",
                formatMoney((BigDecimal) resumen.get("totalMora")), boldSt, moneySt);
        crearFilaResumen(sheet, row++, "Total Saldo Mora",
                formatMoney((BigDecimal) resumen.get("totalSaldoMora")), boldSt, moneySt);
        row++;

        // Filtros aplicados
        Row fRow = sheet.createRow(row++);
        crearCelda(fRow, 0, "FILTROS APLICADOS", headerSt);
        if (filtros.getMunicipios() != null && !filtros.getMunicipios().isEmpty()) {
            crearFilaResumen(sheet, row++, "Municipios",
                    String.join(", ", filtros.getMunicipios()), boldSt, null);
        }
        if (filtros.getEstratosFiltro() != null && !filtros.getEstratosFiltro().isEmpty()) {
            crearFilaResumen(sheet, row++, "Estratos",
                    filtros.getEstratosFiltro().stream()
                            .map(String::valueOf).collect(Collectors.joining(", ")), boldSt, null);
        }
        if (filtros.getMesesReferencia() != null && !filtros.getMesesReferencia().isEmpty()) {
            crearFilaResumen(sheet, row++, "Períodos",
                    String.join(", ", filtros.getMesesReferencia()), boldSt, null);
        }
        if (filtros.isSoloMorosos()) {
            crearFilaResumen(sheet, row++, "Solo morosos", "Sí", boldSt, null);
        }
        row++;

        // Totales por municipio (si disponible)
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> porMunicipio =
                (Map<String, BigDecimal>) resumen.get("totalPorMunicipio");
        if (porMunicipio != null && !porMunicipio.isEmpty()) {
            crearCelda(sheet.createRow(row++), 0, "TOTALES POR MUNICIPIO", headerSt);
            for (Map.Entry<String, BigDecimal> e : porMunicipio.entrySet()) {
                crearFilaResumen(sheet, row++, "  " + e.getKey(),
                        formatMoney(e.getValue()), boldSt, moneySt);
            }
            row++;
        }

        // Totales por mes
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> porMes =
                (Map<String, BigDecimal>) resumen.get("totalPorMes");
        if (porMes != null && !porMes.isEmpty()) {
            crearCelda(sheet.createRow(row++), 0, "TOTALES POR PERÍODO", headerSt);
            for (Map.Entry<String, BigDecimal> e : porMes.entrySet()) {
                crearFilaResumen(sheet, row++, "  " + e.getKey(),
                        formatMoney(e.getValue()), boldSt, moneySt);
            }
            row++;
        }

        // Totales por estrato
        @SuppressWarnings("unchecked")
        Map<Integer, BigDecimal> porEstrato =
                (Map<Integer, BigDecimal>) resumen.get("totalPorEstrato");
        if (porEstrato != null && !porEstrato.isEmpty()) {
            crearCelda(sheet.createRow(row++), 0, "TOTALES POR ESTRATO", headerSt);
            for (Map.Entry<Integer, BigDecimal> e : porEstrato.entrySet()) {
                crearFilaResumen(sheet, row++, "  Estrato " + e.getKey(),
                        formatMoney(e.getValue()), boldSt, moneySt);
            }
        }

        sheet.setColumnWidth(0, 35 * 256);
        sheet.setColumnWidth(1, 25 * 256);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Secciones PDF
    // ══════════════════════════════════════════════════════════════════════════

    private void agregarEncabezadoPdf(Document doc, FiltroReporteDto filtros) throws Exception {
        Font tituloFont = new Font(Font.HELVETICA, 16, Font.BOLD, new Color(0, 84, 166));
        Font normalFont = new Font(Font.HELVETICA, 9);
        Font boldFont   = new Font(Font.HELVETICA, 9, Font.BOLD);

        Paragraph titulo = new Paragraph("EMPRESA DE SERVICIOS PÚBLICOS DEL QUINDÍO - EPQ", tituloFont);
        titulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(titulo);

        Paragraph subtitulo = new Paragraph("REPORTE DE FACTURACIÓN", new Font(Font.HELVETICA, 12, Font.BOLD));
        subtitulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(subtitulo);

        doc.add(new Paragraph("Fecha: " + LocalDate.now().format(DATE_FORMATTER), normalFont));

        if (filtros.getMunicipios() != null && !filtros.getMunicipios().isEmpty()) {
            doc.add(new Paragraph("Municipios: " + String.join(", ", filtros.getMunicipios()), boldFont));
        }
        if (filtros.getEstratosFiltro() != null && !filtros.getEstratosFiltro().isEmpty()) {
            doc.add(new Paragraph("Estratos: " + filtros.getEstratosFiltro().stream()
                    .map(String::valueOf).collect(Collectors.joining(", ")), boldFont));
        }
        if (filtros.getMesesReferencia() != null && !filtros.getMesesReferencia().isEmpty()) {
            doc.add(new Paragraph("Períodos: " + String.join(", ", filtros.getMesesReferencia()), boldFont));
        }
        if (filtros.isSoloMorosos()) {
            doc.add(new Paragraph("Filtro: Solo morosos", boldFont));
        }
        doc.add(new Paragraph(" "));
    }

    private void agregarSeccionMesPdf(Document doc, List<Factura> facturas) throws Exception {
        agregarTituloPdf(doc, "REPORTE AGRUPADO POR PERÍODO");

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{20, 10, 20, 20, 20});

        String[] headers = {"Período", "Cantidad", "Total Factura", "Total Mora", "Total a Pagar"};
        for (String h : headers) agregarCeldaHeaderPdf(table, h);

        Map<String, List<Factura>> porMes = facturas.stream()
                .filter(f -> f.getMesReferencia() != null)
                .collect(Collectors.groupingBy(Factura::getMesReferencia,
                        TreeMap::new, Collectors.toList()));

        for (Map.Entry<String, List<Factura>> e : porMes.entrySet()) {
            List<Factura> g = e.getValue();
            table.addCell(e.getKey());
            table.addCell(String.valueOf(g.size()));
            table.addCell(formatMoney(sumar(g, Factura::getValorFactura)));
            table.addCell(formatMoney(sumar(g, Factura::getValorMora)));
            table.addCell(formatMoney(sumar(g, Factura::getTotalPagar)));
        }
        doc.add(table);
    }

    private void agregarSeccionMunicipioPdf(Document doc, List<Factura> facturas) throws Exception {
        agregarTituloPdf(doc, "TOTALES POR MUNICIPIO");

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{25, 10, 20, 20, 20});

        String[] headers = {"Municipio", "Cantidad", "Total Factura", "Total Mora", "Total a Pagar"};
        for (String h : headers) agregarCeldaHeaderPdf(table, h);

        Map<String, List<Factura>> porMunicipio = facturas.stream()
                .collect(Collectors.groupingBy(Factura::getMunicipio,
                        TreeMap::new, Collectors.toList()));

        for (Map.Entry<String, List<Factura>> e : porMunicipio.entrySet()) {
            List<Factura> g = e.getValue();
            table.addCell(e.getKey());
            table.addCell(String.valueOf(g.size()));
            table.addCell(formatMoney(sumar(g, Factura::getValorFactura)));
            table.addCell(formatMoney(sumar(g, Factura::getValorMora)));
            table.addCell(formatMoney(sumar(g, Factura::getTotalPagar)));
        }
        doc.add(table);
    }

    private void agregarSeccionEstratoPdf(Document doc, List<Factura> facturas) throws Exception {
        agregarTituloPdf(doc, "TOTALES POR ESTRATO SOCIOECONÓMICO");

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{20, 15, 25, 25});

        String[] headers = {"Estrato", "Cantidad", "Total Mora", "Total a Pagar"};
        for (String h : headers) agregarCeldaHeaderPdf(table, h);

        Map<Integer, List<Factura>> porEstrato = facturas.stream()
                .filter(f -> f.getEstrato() != null)
                .collect(Collectors.groupingBy(Factura::getEstrato,
                        TreeMap::new, Collectors.toList()));

        for (Map.Entry<Integer, List<Factura>> e : porEstrato.entrySet()) {
            List<Factura> g = e.getValue();
            table.addCell("Estrato " + e.getKey());
            table.addCell(String.valueOf(g.size()));
            table.addCell(formatMoney(sumar(g, Factura::getValorMora)));
            table.addCell(formatMoney(sumar(g, Factura::getTotalPagar)));
        }
        doc.add(table);
    }

    private void agregarSeccionMorososPdf(Document doc, List<Factura> facturas) throws Exception {
        List<Factura> morosos = facturas.stream()
                .filter(Factura::tieneMora)
                .sorted(Comparator.comparing(Factura::getMunicipio,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());

        agregarTituloPdf(doc, "DEUDORES MOROSOS (" + morosos.size() + " registros)");

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{15, 8, 20, 12, 15, 15, 10});

        String[] headers = {"Municipio", "Estrato", "Dirección", "Período",
                            "Valor Mora", "Saldo Mora", "Estado"};
        for (String h : headers) agregarCeldaHeaderPdf(table, h);

        for (Factura f : morosos) {
            agregarCeldaMorosaPdf(table, f.getMunicipio());
            agregarCeldaMorosaPdf(table, f.getEstrato() != null ? "E" + f.getEstrato() : "-");
            agregarCeldaMorosaPdf(table, nvl(f.getDireccionServicio()));
            agregarCeldaMorosaPdf(table, nvl(f.getMesReferencia()));
            agregarCeldaMorosaPdf(table, formatMoney(f.getValorMora()));
            agregarCeldaMorosaPdf(table, formatMoney(f.getSaldoMora()));
            agregarCeldaMorosaPdf(table, nvl(f.getEstadoPago()));
        }
        doc.add(table);
    }

    private void agregarSeccionDetallePdf(Document doc, List<Factura> facturas) throws Exception {
        agregarTituloPdf(doc, "DETALLE DE VIVIENDA");

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{15, 8, 20, 12, 15, 15});

        String[] headers = {"Municipio", "Estrato", "Dirección", "Período",
                            "Valor Factura", "Total a Pagar"};
        for (String h : headers) agregarCeldaHeaderPdf(table, h);

        for (Factura f : facturas) {
            table.addCell(nvl(f.getMunicipio()));
            table.addCell(f.getEstrato() != null ? String.valueOf(f.getEstrato()) : "-");
            table.addCell(nvl(f.getDireccionServicio()));
            table.addCell(nvl(f.getMesReferencia()));
            table.addCell(formatMoney(f.getValorFactura()));
            table.addCell(formatMoney(f.getTotalPagar()));
        }
        doc.add(table);
    }

    private void agregarSeccionDatosPdf(Document doc, List<Factura> facturas) throws Exception {
        agregarTituloPdf(doc, "DATOS DE FACTURACIÓN");

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{18, 8, 12, 20, 20, 12});

        String[] headers = {"Municipio", "Estrato", "Período",
                            "Valor Factura", "Total a Pagar", "Estado"};
        for (String h : headers) agregarCeldaHeaderPdf(table, h);

        for (Factura f : facturas) {
            table.addCell(nvl(f.getMunicipio()));
            table.addCell(f.getEstrato() != null ? String.valueOf(f.getEstrato()) : "-");
            table.addCell(nvl(f.getMesReferencia()));
            table.addCell(formatMoney(f.getValorFactura()));
            table.addCell(formatMoney(f.getTotalPagar()));
            table.addCell(nvl(f.getEstadoPago()));
        }
        doc.add(table);
    }

    @SuppressWarnings("unchecked")
    private void agregarResumenPdf(Document doc, Map<String, Object> resumen) throws Exception {
        agregarTituloPdf(doc, "RESUMEN GENERAL");
        Font bold = new Font(Font.HELVETICA, 10, Font.BOLD);
        Font normal = new Font(Font.HELVETICA, 9);

        doc.add(new Paragraph("Total registros: " + resumen.get("totalFacturas"), normal));
        doc.add(new Paragraph("Total morosos:   " + resumen.get("totalMorosos"), normal));
        doc.add(new Paragraph("Total general:   " + formatMoney((BigDecimal) resumen.get("totalGeneral")), bold));
        doc.add(new Paragraph("Total mora:      " + formatMoney((BigDecimal) resumen.get("totalMora")), bold));
        doc.add(new Paragraph("Total saldo mora:" + formatMoney((BigDecimal) resumen.get("totalSaldoMora")), bold));
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers Excel
    // ══════════════════════════════════════════════════════════════════════════

    private CellStyle estiloEncabezado(XSSFWorkbook wb) {
        CellStyle st = wb.createCellStyle();
        org.apache.poi.xssf.usermodel.XSSFFont f =
                (org.apache.poi.xssf.usermodel.XSSFFont) wb.createFont();
        f.setBold(true);
        f.setColor(new XSSFColor(Color.WHITE, null));
        st.setFont(f);
        st.setFillForegroundColor(new XSSFColor(COLOR_ENCABEZADO, null));
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        st.setAlignment(HorizontalAlignment.CENTER);
        st.setBorderBottom(BorderStyle.THIN);
        st.setBorderTop(BorderStyle.THIN);
        st.setBorderLeft(BorderStyle.THIN);
        st.setBorderRight(BorderStyle.THIN);
        return st;
    }

    private CellStyle estiloMoneda(XSSFWorkbook wb) {
        CellStyle st = wb.createCellStyle();
        st.setDataFormat(wb.createDataFormat().getFormat("$ #,##0.00"));
        return st;
    }

    private CellStyle estiloBold(XSSFWorkbook wb) {
        CellStyle st = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font f = wb.createFont();
        f.setBold(true);
        st.setFont(f);
        return st;
    }

    private CellStyle estiloFondoColor(XSSFWorkbook wb, Color color) {
        CellStyle st = wb.createCellStyle();
        st.setFillForegroundColor(new XSSFColor(color, null));
        st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return st;
    }

    private void crearFilaTitulo(Sheet sheet, XSSFWorkbook wb,
                                  String titulo, int cols, int rowNum) {
        Row r = sheet.createRow(rowNum);
        Cell c = r.createCell(0);
        c.setCellValue(titulo);
        CellStyle st = wb.createCellStyle();
        org.apache.poi.xssf.usermodel.XSSFFont f =
                (org.apache.poi.xssf.usermodel.XSSFFont) wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 13);
        f.setColor(new XSSFColor(COLOR_ENCABEZADO, null));
        st.setFont(f);
        c.setCellStyle(st);
        if (cols > 1) {
            sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, cols - 1));
        }
    }

    private void crearCelda(Row row, int col, String valor, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(valor != null ? valor : "");
        if (style != null) c.setCellStyle(style);
    }

    private void crearCeldaNum(Row row, int col, double valor, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(valor);
        if (style != null) c.setCellStyle(style);
    }

    private void crearFilaResumen(Sheet sheet, int rowNum,
                                   String etiqueta, String valor,
                                   CellStyle etiSt, CellStyle valSt) {
        Row r = sheet.createRow(rowNum);
        crearCelda(r, 0, etiqueta, etiSt);
        crearCelda(r, 1, valor, valSt);
    }

    private void autoSize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            try { sheet.autoSizeColumn(i); }
            catch (Exception ignored) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Helpers PDF
    // ══════════════════════════════════════════════════════════════════════════

    private void agregarTituloPdf(Document doc, String titulo) throws Exception {
        Font f = new Font(Font.HELVETICA, 11, Font.BOLD, COLOR_ENCABEZADO);
        Paragraph p = new Paragraph(titulo, f);
        p.setSpacingBefore(8);
        p.setSpacingAfter(4);
        doc.add(p);
    }

    private void agregarCeldaHeaderPdf(PdfPTable table, String texto) {
        Font f = new Font(Font.HELVETICA, 8, Font.BOLD, Color.WHITE);
        PdfPCell cell = new PdfPCell(new Phrase(texto, f));
        cell.setBackgroundColor(COLOR_ENCABEZADO);
        cell.setPadding(4);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void agregarCeldaMorosaPdf(PdfPTable table, String texto) {
        Font f = new Font(Font.HELVETICA, 8);
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", f));
        cell.setBackgroundColor(new Color(255, 235, 238));
        cell.setPadding(3);
        table.addCell(cell);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Utilidades generales
    // ══════════════════════════════════════════════════════════════════════════

    private void validarDatos(List<Factura> facturas) {
        if (facturas == null || facturas.isEmpty()) {
            throw new EpqException(ErrorCode.REPORTE_SIN_DATOS,
                    "No hay datos que coincidan con los filtros seleccionados. " +
                    "Verifique municipios, períodos y demás criterios.");
        }
    }

    private List<String> resolverTipos(List<String> tipos) {
        if (tipos == null || tipos.isEmpty()) {
            // Sin tipo especificado → generar reporte combinado completo
            return List.of("DEUDORES_MOROSOS", "TOTALES_MUNICIPIO",
                           "TOTALES_ESTRATO", "AGRUPADO_MES", "DETALLE_VIVIENDA");
        }
        // Si piden COMBINADO, expandir
        if (tipos.contains("COMBINADO")) {
            return List.of("DEUDORES_MOROSOS", "TOTALES_MUNICIPIO",
                           "TOTALES_ESTRATO", "AGRUPADO_MES", "DETALLE_VIVIENDA");
        }
        return tipos;
    }

    @FunctionalInterface
    private interface GetterBD { BigDecimal get(Factura f); }

    private BigDecimal sumar(List<Factura> lista, GetterBD getter) {
        return lista.stream()
                .map(getter::get)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static double bd(BigDecimal v) {
        return v != null ? v.doubleValue() : 0.0;
    }

    private static String nvl(String s) {
        return s != null ? s : "";
    }

    private static String formatMoney(BigDecimal v) {
        if (v == null) return "$ 0";
        return "$ " + CURRENCY_FORMAT.format(v);
    }
}
