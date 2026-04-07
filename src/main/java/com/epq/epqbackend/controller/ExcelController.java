package com.epq.epqbackend.controller;

import com.epq.epqbackend.dto.ArchivoExcelDto;
import com.epq.epqbackend.service.ExcelService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/excel")
@CrossOrigin(origins = "*")
public class ExcelController {

    @Autowired
    private ExcelService excelService;

    @PostMapping("/cargar")
    public ResponseEntity<String> cargarExcel(@RequestParam("archivo") MultipartFile archivo) {

        excelService.guardarExcelExtra(archivo);

        return ResponseEntity.ok("Excel cargado correctamente");
    }

    @GetMapping
    public ResponseEntity<List<ArchivoExcelDto>> listarExcels() {

        List<String> archivos = excelService.listarExcelsExtra();

        List<ArchivoExcelDto> response = archivos.stream()
                .map(nombre -> {
                    ArchivoExcelDto dto = new ArchivoExcelDto();

                    dto.setNombreArchivo(nombre);
                    dto.setPeso(0L);
                    dto.setFechaCarga(null);
                    dto.setTipo("extra");
                    return dto;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{nombre}")
    public ResponseEntity<String> eliminarExcel(@PathVariable String nombre) {

        excelService.eliminarExcel("extra", nombre);

        return ResponseEntity.ok("Excel eliminado");
    }
}