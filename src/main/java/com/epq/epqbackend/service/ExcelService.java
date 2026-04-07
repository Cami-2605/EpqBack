package com.epq.epqbackend.service;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExcelService {

    private final Path basePath = Paths.get("data/excel/base");
    private final Path extraPath = Paths.get("data/excel/extra");

    public ExcelService() throws IOException {
        Files.createDirectories(basePath);
        Files.createDirectories(extraPath);
    }

    public List<String> listarExcelsBase() {
        return listarArchivos(basePath);
    }

    public List<String> listarExcelsExtra() {
        return listarArchivos(extraPath);
    }

    public void guardarExcelExtra(MultipartFile archivo) {
        try {
            Path destino = extraPath.resolve(archivo.getOriginalFilename());
            Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("Error guardando el excel", e);
        }
    }

    public void eliminarExcel(String tipo, String nombre) {
        try {
            Path ruta = tipo.equalsIgnoreCase("base")
                    ? basePath.resolve(nombre)
                    : extraPath.resolve(nombre);

            Files.deleteIfExists(ruta);
        } catch (Exception e) {
            throw new RuntimeException("Error eliminando el excel", e);
        }
    }

    private List<String> listarArchivos(Path ruta) {
        try {
            return Files.list(ruta)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("Error listando excels", e);
        }
    }

    public List<Map<String, String>> leerExcel(String nombreArchivo, String tipo) {

        Path ruta = tipo.equalsIgnoreCase("base")
                ? basePath.resolve(nombreArchivo)
                : extraPath.resolve(nombreArchivo);

        List<Map<String, String>> data = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(Files.newInputStream(ruta))) {

            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(0);

            Map<String, Integer> headers = new HashMap<>();

            for (Cell cell : headerRow) {
                headers.put(
                        normalizar(cell.getStringCellValue()),
                        cell.getColumnIndex()
                );
            }

            if (!headers.containsKey("municipio") || !headers.containsKey("fecha")) {
                throw new RuntimeException("El Excel no contiene columnas obligatorias: municipio, fecha");
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {

                Row row = sheet.getRow(i);
                if (row == null) continue;

                Map<String, String> fila = new HashMap<>();

                for (String key : headers.keySet()) {
                    fila.put(key, getCell(row, headers, key));
                }

                data.add(fila);
            }

        } catch (Exception e) {
            throw new RuntimeException("Error leyendo Excel", e);
        }

        return data;
    }

    private String getCell(Row row, Map<String, Integer> headers, String column) {

        Integer index = headers.get(column.toLowerCase());

        if (index == null) return "";

        Cell cell = row.getCell(index);

        if (cell == null) return "";

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();

            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toLocalDate().toString();
                }
                double num = cell.getNumericCellValue();
                if (num == (long) num) {
                    return String.valueOf((long) num);
                }
                return String.valueOf(num);

            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());

            default:
                return "";
        }
    }

    private String normalizar(String texto) {
        return texto
                .toLowerCase()
                .trim()
                .replace(" ", "")
                .replace("í", "i")
                .replace("á", "a")
                .replace("é", "e")
                .replace("ó", "o")
                .replace("ú", "u");
    }
}