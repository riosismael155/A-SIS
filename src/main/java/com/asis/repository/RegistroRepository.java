package com.asis.repository;

import com.asis.model.Empleado;
import com.asis.model.RegistroAsistencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

public interface RegistroRepository extends JpaRepository<RegistroAsistencia, Long> {
    List<RegistroAsistencia> findByEmpleadoDniAndFechaBetween(String dni, LocalDate desde, LocalDate hasta);


    boolean existsByEmpleadoAndFecha(Empleado empleado, LocalDate actual);

    @Transactional
    @Modifying
    @Query("DELETE FROM RegistroAsistencia r WHERE r.fecha >= :desde AND r.fecha <= :hasta")
    void eliminarPorRangoFechas(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

    List<RegistroAsistencia> findByEmpleadoDniAndFecha(String dni, LocalDate fecha);

    List<RegistroAsistencia> findByEmpleadoAndFecha(Empleado empleado, LocalDate fecha);
    @Modifying
    @Transactional
    @Query("DELETE FROM RegistroAsistencia r WHERE r.justificacion IS NULL AND r.fecha BETWEEN :desde AND :hasta")
    void eliminarRegistrosSinJustificacionPorRango(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);


    @Query("SELECT MAX(r.ordenDia) FROM RegistroAsistencia r WHERE r.empleado.id = :empleadoId AND r.fecha = :fecha")
    Integer findMaxOrdenDiaByEmpleadoAndFecha(@Param("empleadoId") Long empleadoId, @Param("fecha") LocalDate fecha);

}

