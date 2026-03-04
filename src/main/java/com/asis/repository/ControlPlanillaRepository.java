package com.asis.repository;

import com.asis.model.ControlPlanilla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ControlPlanillaRepository extends JpaRepository<ControlPlanilla, Long> {
}
