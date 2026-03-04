package com.asis.repository;


import com.asis.model.LogCargaAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
@Repository

public interface LogCargaAsistenciaRepository extends JpaRepository<LogCargaAsistencia, Long> {

    // Obtener todos los logs ordenados por fecha de carga descendente
    List<LogCargaAsistencia> findAllByOrderByFechaCargaDesc();

    // (Opcional) Obtener logs que contienen registros de una fecha específica
    List<LogCargaAsistencia> findByDesdeLessThanEqualAndHastaGreaterThanEqual(LocalDate fecha1, LocalDate fecha2);

    List<LogCargaAsistencia> findTop12ByOrderByFechaCargaDesc();

}
