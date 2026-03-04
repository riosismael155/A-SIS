package com.asis.service;

import com.asis.model.HoraEnPlanilla;
import com.asis.model.SemanaEmpleado;
import com.asis.repository.HoraEnPlanillaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class HoraEnPlanillaService {

    private final HoraEnPlanillaRepository repo;

    public HoraEnPlanillaService(HoraEnPlanillaRepository repo) {
        this.repo = repo;
    }

    public HoraEnPlanilla agregarHoras(
            SemanaEmpleado se,
            LocalDate fecha,
            int extra,
            int findeFeriado
    ) {
        validarFechaEnSemana(se, fecha);

        HoraEnPlanilla h = new HoraEnPlanilla();
        h.setSemanaEmpleado(se);
        h.setFecha(fecha);
        h.setExtra(extra);
        h.setFindeFeriado(findeFeriado);

        return repo.save(h);
    }

    private void validarFechaEnSemana(SemanaEmpleado se, LocalDate fecha) {
        if (fecha.isBefore(se.getSemana().getDesde())
                || fecha.isAfter(se.getSemana().getHasta())) {
            throw new IllegalArgumentException(
                    "La fecha está fuera del rango de la semana");
        }
    }
}
