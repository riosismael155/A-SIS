package com.asis.repository;


import com.asis.model.Empleado;
import com.asis.model.Ausencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JustificacionAusenciaRepository extends JpaRepository<Ausencia, Long> {

    Optional<Ausencia> findById(Long id);
    @Query("SELECT a FROM Ausencia a WHERE a.empleado = :empleado AND :fecha BETWEEN a.desde AND a.hasta")
    Optional<Ausencia> findByEmpleadoAndFecha(@Param("empleado") Empleado empleado,
                                              @Param("fecha") LocalDate fecha);
}