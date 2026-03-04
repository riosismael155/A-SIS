package com.asis.service;

import com.asis.model.ControlPlanilla;
import com.asis.model.Empleado;
import com.asis.model.Semana;
import com.asis.repository.ControlPlanillaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class ControlPlanillaService {

    private final ControlPlanillaRepository controlPlanillaRepository;

    public ControlPlanillaService(ControlPlanillaRepository controlPlanillaRepository) {
        this.controlPlanillaRepository = controlPlanillaRepository;
    }

    /**
     * Crea un ControlPlanilla usando la fecha actual del sistema
     */
    public ControlPlanilla crearControlPlanilla(String observaciones) {

        LocalDate hoy = LocalDate.now();

        LocalDate desde = hoy.withDayOfMonth(21);
        LocalDate hasta = hoy.plusMonths(1).withDayOfMonth(20);

        String nombre = generarNombre(desde, hasta);

        ControlPlanilla control = new ControlPlanilla();
        control.setDesde(desde);
        control.setHasta(hasta);
        control.setNombre(nombre);
        control.setObservaciones(observaciones);

        // 🔹 Generar semanas
        List<Semana> semanas = generarSemanas(desde, hasta);
        for (Semana s : semanas) {
            s.setControlPlanilla(control);
        }
        control.setSemanas(semanas);

        return controlPlanillaRepository.save(control);
    }



    private String generarNombre(LocalDate desde, LocalDate hasta) {
        String mesDesde = desde.getMonth()
                .getDisplayName(TextStyle.FULL, new Locale("es", "ES"));

        String mesHasta = hasta.getMonth()
                .getDisplayName(TextStyle.FULL, new Locale("es", "ES"));

        return mesDesde + "-" + mesHasta + " " + hasta.getYear();
    }

    private List<Semana> generarSemanas(LocalDate desde, LocalDate hasta) {

        List<Semana> semanas = new ArrayList<>();
        LocalDate cursor = desde;

        // 1️⃣ Primera semana parcial
        if (cursor.getDayOfWeek() != DayOfWeek.MONDAY) {

            LocalDate finSemana = cursor.with(DayOfWeek.SUNDAY);
            if (finSemana.isAfter(hasta)) {
                finSemana = hasta;
            }

            Semana semana = new Semana();
            semana.setDesde(cursor);
            semana.setHasta(finSemana);
            semanas.add(semana);

            cursor = finSemana.plusDays(1);
        }

        // 2️⃣ Semanas completas lunes → domingo
        while (!cursor.isAfter(hasta)) {

            if (cursor.plusDays(6).isAfter(hasta)) {
                break;
            }

            Semana semana = new Semana();
            semana.setDesde(cursor);
            semana.setHasta(cursor.plusDays(6));
            semanas.add(semana);

            cursor = cursor.plusDays(7);
        }

        // 3️⃣ Última semana parcial
        if (!cursor.isAfter(hasta)) {

            Semana semana = new Semana();
            semana.setDesde(cursor);
            semana.setHasta(hasta);
            semanas.add(semana);
        }

        return semanas;
    }

    public List<ControlPlanilla> findAll() {
        return controlPlanillaRepository.findAll();
    }

    public Optional<ControlPlanilla> findById(Long id) {
        return controlPlanillaRepository.findById(id);
    }

    public ControlPlanilla save(ControlPlanilla controlPlanilla) {
        return controlPlanillaRepository.save(controlPlanilla);
    }

    @Transactional
    public void actualizarObservaciones(Long id, String observaciones) {
        ControlPlanilla cp = controlPlanillaRepository.findById(id).orElseThrow();
        cp.setObservaciones(observaciones);
        // Si tienes campo de fecha de modificación:
        // cp.setFechaModificacion(LocalDateTime.now());
        controlPlanillaRepository.save(cp);
    }
}
