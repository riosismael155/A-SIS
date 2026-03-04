package com.asis.service;

import com.asis.model.ControlPlanilla;
import com.asis.model.Semana;
import com.asis.repository.SemanaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SemanaService {

    private final SemanaRepository repo;

    public SemanaService(SemanaRepository repo) {
        this.repo = repo;
    }

    public Semana obtenerSemanaInicial(ControlPlanilla cp) {
        return repo.findFirstByControlPlanillaIdOrderByDesdeAsc(cp.getId())
                .orElseThrow();
    }

    public Semana obtenerSemanaSiguiente(ControlPlanilla cp, Semana actual) {

        return repo.findByControlPlanillaIdAndDesde(
                cp.getId(),
                actual.getHasta().plusDays(1)
        ).orElse(actual);
    }

    public Semana obtenerSemanaAnterior(ControlPlanilla cp, Semana actual) {

        return repo.findByControlPlanillaIdAndDesde(
                cp.getId(),
                actual.getDesde().minusDays(7)
        ).orElse(actual);
    }

    public Semana findById(Long id) {
        return repo.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Semana no encontrada"));
    }


        public Semana getAnterior(ControlPlanilla cp, Semana actual) {
            List<Semana> semanas = cp.getSemanas();

            int index = semanas.indexOf(actual);
            return (index > 0) ? semanas.get(index - 1) : null;
        }

        public Semana getSiguiente(ControlPlanilla cp, Semana actual) {
            List<Semana> semanas = cp.getSemanas();

            int index = semanas.indexOf(actual);
            return (index < semanas.size() - 1) ? semanas.get(index + 1) : null;
        }


    }





