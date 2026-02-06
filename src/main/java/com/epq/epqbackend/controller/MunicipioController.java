package com.epq.epqbackend.controller;

import com.epq.epqbackend.dto.MunicipioDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/municipios")
public class MunicipioController {

    @GetMapping
    public ResponseEntity<List<MunicipioDto>> listarMunicipios() {
        return ResponseEntity.ok(new ArrayList<>());
    }
}