package com.asis.repository;

import com.asis.model.Feriado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository

public interface FeriadoRepository extends JpaRepository<Feriado, Long> {

    List<Feriado> findAll();

    List<Feriado> findAllByOrderByMesAscDiaAsc();


}

