package com.epq.epqbackend.repository;

import com.epq.epqbackend.model.ArchivoExcel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ArchivoExcelRepository extends JpaRepository<ArchivoExcel, Long> {
    List<ArchivoExcel> findByTipo(String tipo);
    boolean existsByNombreArchivo(String nombreArchivo);
    void deleteByNombreArchivo(String nombreArchivo);
}