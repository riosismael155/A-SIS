package com.asis.service;

import com.asis.model.Guardia;
import com.asis.model.SemanaEmpleado;
import com.asis.repository.GuardiaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;


@Service
@Transactional
public class GuardiaService {

    private final GuardiaRepository repo;

    public GuardiaService(GuardiaRepository repo) {
        this.repo = repo;
    }

    public Guardia agregarGuardia(
            SemanaEmpleado se,
            LocalDate fecha,
            int cant
    ) {
        validarFechaEnSemana(se, fecha);

        Guardia g = new Guardia();
        g.setSemanaEmpleado(se);
        g.setFecha(fecha);
        g.setCant(cant);

        return repo.save(g);
    }

    private void validarFechaEnSemana(SemanaEmpleado se, LocalDate fecha) {
        if (fecha.isBefore(se.getSemana().getDesde())
                || fecha.isAfter(se.getSemana().getHasta())) {
            throw new IllegalArgumentException(
                    "La fecha está fuera del rango de la semana");
        }
    }
}
