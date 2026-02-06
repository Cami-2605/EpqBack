package com.epq.epqbackend.controller;

import com.epq.epqbackend.dto.ArchivoExcelDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/excel")
public class ExcelController {

    @PostMapping("/cargar")
    public ResponseEntity<String> cargarExcel(@RequestParam("archivo") MultipartFile archivo) {
        return ResponseEntity.ok("Excel cargado correctamente");
    }

    @GetMapping
    public ResponseEntity<List<ArchivoExcelDto>> listarExcels() {
        return ResponseEntity.ok(new ArrayList<>());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> eliminarExcel(@PathVariable Long id) {
        return ResponseEntity.ok("Excel eliminado");
    }
}