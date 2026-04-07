package com.epq.epqbackend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.*;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class ReporteService {

    @Autowired
    private ExcelService excelService;

    public byte[] generarReporte(
            String nombreArchivo,
            String tipoFuente,
            LocalDate fechaInicio,
            LocalDate fechaFin,
            String municipio,
            String tipoReporte,
            String formato
    ) {

        // 🔥 VALIDACIONES (MUY IMPORTANTES)
        if (nombreArchivo == null || nombreArchivo.isEmpty()) {
            throw new RuntimeException("Debe seleccionar un archivo");
        }

        if (municipio == null || municipio.isEmpty()) {
            throw new RuntimeException("Debe seleccionar un municipio");
        }

        if (tipoReporte == null || tipoReporte.isEmpty()) {
            throw new RuntimeException("Debe seleccionar un tipo de reporte");
        }

        // 🔹 Leer Excel
        List<Map<String, String>> data = excelService.leerExcel(nombreArchivo, tipoFuente);

        // 🔹 Filtrar
        List<Map<String, String>> filtrado = data.stream()

                .filter(d -> d.get("municipio") != null &&
                        d.get("municipio").equalsIgnoreCase(municipio))

                .filter(d -> {
                    try {
                        LocalDate fecha = LocalDate.parse(d.get("fecha"));
                        return (fechaInicio == null || !fecha.isBefore(fechaInicio)) &&
                                (fechaFin == null || !fecha.isAfter(fechaFin));
                    } catch (Exception e) {
                        return false;
                    }
                })

                .map(d -> {
                    Map<String, String> r = new HashMap<>();

                    r.put("municipio", d.getOrDefault("municipio", ""));
                    r.put("fecha", d.getOrDefault("fecha", ""));

                    // 🔥 dinámico según columna
                    r.put("valor", d.getOrDefault(tipoReporte.toLowerCase(), "0"));

                    return r;
                })

                .collect(Collectors.toList());

        // 🔥 VALIDACIÓN SI NO HAY DATOS
        if (filtrado.isEmpty()) {
            throw new RuntimeException("No hay datos para los filtros seleccionados");
        }

        // 🔹 Generar archivo
        if (formato != null && formato.equalsIgnoreCase("pdf")) {
            return generarPdf(filtrado);
        } else {
            return generarExcel(filtrado);
        }
    }

    // ================= EXCEL =================

    private byte[] generarExcel(List<Map<String, String>> data) {

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Reporte");

            // 🔹 Header con estilo simple
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Municipio");
            header.createCell(1).setCellValue("Fecha");
            header.createCell(2).setCellValue("Valor");

            int rowNum = 1;

            for (Map<String, String> d : data) {

                Row row = sheet.createRow(rowNum++);

                row.createCell(0).setCellValue(d.get("municipio"));
                row.createCell(1).setCellValue(d.get("fecha"));
                row.createCell(2).setCellValue(d.get("valor"));
            }

            // 🔥 Ajustar columnas automáticamente
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);

            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel", e);
        }
    }

    // ================= PDF =================

    private byte[] generarPdf(List<Map<String, String>> data) {

        try {
            Document document = new Document();
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            PdfWriter.getInstance(document, out);

            document.open();

            document.add(new Paragraph("REPORTE EPQ"));
            document.add(new Paragraph(" "));

            for (Map<String, String> d : data) {
                document.add(new Paragraph(
                        "Municipio: " + d.get("municipio") +
                                " | Fecha: " + d.get("fecha") +
                                " | Valor: " + d.get("valor")
                ));
            }

            document.close();

            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF", e);
        }
    }
}