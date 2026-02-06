package com.epq.epqbackend.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
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
}