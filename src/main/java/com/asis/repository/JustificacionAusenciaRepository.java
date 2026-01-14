package com.asis.repository;


import com.asis.model.Ausencia;
import com.asis.model.Empleado;
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

    @Query("""
                SELECT a FROM Ausencia a
                JOIN FETCH a.empleado e
                WHERE e.id = :empleadoId
                AND a.tipoDeAusencia <> :tipoExcluido
                ORDER BY a.desde DESC
            """)
    List<Ausencia> buscarAusenciasPorEmpleado(@Param("empleadoId") Long empleadoId,
                                              @Param("tipoExcluido") Ausencia.TipoDeAusencia tipoExcluido);

    @Query("SELECT a FROM Ausencia a JOIN FETCH a.empleado e ORDER BY a.id DESC")
    List<Ausencia> findAllOrderByIdDesc();

    @Query("SELECT a FROM Ausencia a WHERE a.empleado = :empleado " +
            "AND (a.desde <= :hasta AND a.hasta >= :desde)")
    List<Ausencia> findByEmpleadoAndFechasSolapadas(
            @Param("empleado") Empleado empleado,
            @Param("desde") LocalDate desde,
            @Param("hasta") LocalDate hasta);




}