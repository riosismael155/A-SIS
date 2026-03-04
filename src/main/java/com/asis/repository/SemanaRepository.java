package com.asis.repository;

import com.asis.model.Semana;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
@Repository
public interface SemanaRepository extends JpaRepository<Semana, Long> {

    Optional<Semana> findByControlPlanillaIdAndDesde(
            Long controlPlanillaId,
            LocalDate desde
    );

    Optional<Semana> findFirstByControlPlanillaIdOrderByDesdeAsc(Long id);

    Optional<Semana> findFirstByControlPlanillaIdOrderByDesdeDesc(Long id);


}
