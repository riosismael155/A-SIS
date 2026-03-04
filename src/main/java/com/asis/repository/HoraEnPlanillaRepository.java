package com.asis.repository;

import com.asis.model.HoraEnPlanilla;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HoraEnPlanillaRepository extends JpaRepository<HoraEnPlanilla, Integer> {
}
