package com.asis.repository;

import com.asis.model.Guardia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuardiaRepository extends JpaRepository<Guardia, Integer> {
    List<Guardia> findBySemanaEmpleadoId(Long semanaEmpleadoId);
}

