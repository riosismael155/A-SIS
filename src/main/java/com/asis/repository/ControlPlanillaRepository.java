package com.asis.repository;

import com.asis.model.ControlPlanilla;
import com.asis.model.SemanaEmpleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ControlPlanillaRepository extends JpaRepository<ControlPlanilla, Long> {
}
