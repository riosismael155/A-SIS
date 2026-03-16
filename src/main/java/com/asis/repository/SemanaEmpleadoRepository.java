package com.asis.repository;

import com.asis.model.SemanaEmpleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
@Repository

public interface SemanaEmpleadoRepository extends JpaRepository<SemanaEmpleado, Long> {

    boolean existsBySemanaIdAndEmpleadoId(Long semanaId, Long empleadoId);

    List<SemanaEmpleado> findBySemanaIdOrderByIdAsc(Long semanaId);

    @Query("SELECT se FROM SemanaEmpleado se " +
            "JOIN FETCH se.empleado e " +  // Para cargar el empleado
            "WHERE (se.semana.desde BETWEEN :desde AND :hasta) " +
            "OR (se.semana.hasta BETWEEN :desde AND :hasta) " +
            "OR (se.semana.desde <= :desde AND se.semana.hasta >= :hasta) " +
            "ORDER BY e.apellido, e.nombre")
    List<SemanaEmpleado> findAllByPeriodo(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);

}
