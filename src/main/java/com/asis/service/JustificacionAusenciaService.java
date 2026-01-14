package com.asis.service;

import com.asis.model.Ausencia;
import com.asis.model.Empleado;
import com.asis.model.RegistroAsistencia;
import com.asis.repository.EmpleadoRepository;
import com.asis.repository.JustificacionAusenciaRepository;
import com.asis.repository.RegistroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class JustificacionAusenciaService {

    private final EmpleadoRepository empleadoRepo;
    private final RegistroRepository registroRepo;
    private final JustificacionAusenciaRepository justificacionRepo;
    private final FeriadoService feriadoService;

    @Transactional
    public void justificarAusencia(String dni, LocalDate desde, LocalDate hasta, String descripcion, Ausencia.TipoDeAusencia tipoAusencia) {
        Empleado empleado = empleadoRepo.findByDni(dni)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado con DNI: " + dni));

        // Verificar si ya existen ausencias justificadas en el rango de fechas
        List<Ausencia> ausenciasExistentes = justificacionRepo.findByEmpleadoAndFechasSolapadas(empleado, desde, hasta);

        if (!ausenciasExistentes.isEmpty()) {
            // Crear mensaje de error simple
            StringBuilder mensajeError = new StringBuilder();
            mensajeError.append("El empleado ya tiene ausencias justificadas en el rango seleccionado. ");
            mensajeError.append("Ausencias existentes: ");

            for (Ausencia ausencia : ausenciasExistentes) {
                mensajeError.append("Del ")
                        .append(ausencia.getDesde().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                        .append(" al ")
                        .append(ausencia.getHasta().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")))
                        .append(" (").append(ausencia.getTipoDeAusencia().toString()).append("); ");
            }

            throw new IllegalArgumentException(mensajeError.toString());
        }

      // 1. Calcular horas de cada turno
        double horasTurno1 = Duration.between(empleado.getHoraEntrada(), empleado.getHoraSalida()).toMinutes() / 60.0;
        double horasTurno2 = 0.0;
        boolean tieneDosTurnos = false;

        if (empleado.getHoraEntrada2() != null && empleado.getHoraSalida2() != null) {
            horasTurno2 = Duration.between(empleado.getHoraEntrada2(), empleado.getHoraSalida2()).toMinutes() / 60.0;
            tieneDosTurnos = true;
        }

        // 2. Determinar si los turnos tienen la misma cantidad de horas
        boolean turnosIgualHoras = tieneDosTurnos && Math.abs(horasTurno1 - horasTurno2) < 0.01;

        // 3. Calcular horas totales según la lógica
        double horasTotales;
        if (tieneDosTurnos) {
            if (turnosIgualHoras) {
                // Si los turnos son iguales, usar solo el primero
                horasTotales = horasTurno1;
            } else {
                // Si los turnos son diferentes, sumar ambos
                horasTotales = horasTurno1 + horasTurno2;
            }
        } else {
            // Solo tiene un turno
            horasTotales = horasTurno1;
        }

        // 4. Determinar las horas de inicio y fin
        LocalTime horaInicioJustificacion;
        LocalTime horaFinJustificacion;

        if (tieneDosTurnos && !turnosIgualHoras) {
            // Cuando los turnos son diferentes, comenzar a las 7:00
            horaInicioJustificacion = LocalTime.of(7, 0);
            horaFinJustificacion = horaInicioJustificacion.plusMinutes((long)(horasTotales * 60));
        } else {
            // Cuando los turnos son iguales o solo hay un turno, usar el primer turno
            horaInicioJustificacion = empleado.getHoraEntrada();
            horaFinJustificacion = empleado.getHoraSalida();
        }

        // Crear la justificación
        Ausencia justificacion = Ausencia.builder()
                .empleado(empleado)
                .desde(desde)
                .hasta(hasta)
                .descripcion(descripcion)
                .tipoDeAusencia(tipoAusencia)
                .build();

        LocalDate actual = desde;

        while (!actual.isAfter(hasta)) {
            boolean esFinde = actual.getDayOfWeek() == DayOfWeek.SATURDAY || actual.getDayOfWeek() == DayOfWeek.SUNDAY;
            boolean esFeriado = feriadoService.esFeriado(actual);
            boolean esLaboral = !esFinde && !esFeriado;

            // Determinar si generar registros según el tipo de ausencia
            boolean generarRegistros;

            if (tipoAusencia == Ausencia.TipoDeAusencia.NO_MARCO) {
                // Para NO_MARCO, generar registros en TODOS los días (laborales y no laborales)
                generarRegistros = true;
            } else {
                // Para otros tipos de ausencia, solo generar en días laborales
                generarRegistros = esLaboral;
            }

            if (generarRegistros) {
                // Verificar si ya existen registros para este empleado y fecha
                List<RegistroAsistencia> existentes = registroRepo.findByEmpleadoAndFecha(empleado, actual);
                if (existentes.isEmpty()) {
                    // Crear registro de entrada
                    RegistroAsistencia entrada = new RegistroAsistencia();
                    entrada.setEmpleado(empleado);
                    entrada.setFecha(actual);
                    entrada.setHora(horaInicioJustificacion);
                    entrada.setOrdenDia(1);
                    entrada.setTipoHora(RegistroAsistencia.TipoHora.NORMAL);
                    entrada.setJustificacion(justificacion);

                    // Crear registro de salida
                    RegistroAsistencia salida = new RegistroAsistencia();
                    salida.setEmpleado(empleado);
                    salida.setFecha(actual);
                    salida.setHora(horaFinJustificacion);
                    salida.setOrdenDia(2);
                    salida.setTipoHora(RegistroAsistencia.TipoHora.NORMAL);
                    salida.setJustificacion(justificacion);

                    // Añadir al padre (bidireccionalidad)
                    justificacion.getRegistrosGenerados().add(entrada);
                    justificacion.getRegistrosGenerados().add(salida);
                } else {
                    // Actualizar registros existentes para que apunten a la justificación
                    for (RegistroAsistencia r : existentes) {
                        r.setJustificacion(justificacion);
                        justificacion.getRegistrosGenerados().add(r);
                    }
                }
            }

            actual = actual.plusDays(1);
        }

        // Guardar la justificación, se persisten los registros por cascade
        justificacionRepo.save(justificacion);
    }


    public List<Ausencia> listarJustificaciones() {
        return justificacionRepo.findAll();
    }


    @Transactional
    public void eliminarJustificacion(Long id) {
        if (!justificacionRepo.existsById(id)) {
            throw new IllegalArgumentException("No existe la justificación con id: " + id);
        }
        justificacionRepo.deleteById(id);
    }

    public Ausencia obtenerAusenciaEmpleadoEnFecha(Empleado empleado, LocalDate fecha) {
        return justificacionRepo.findByEmpleadoAndFecha(empleado, fecha).orElse(null);
    }

    public List<Ausencia> buscarAusenciasJustificadasPorEmpleadoId(Long empleadoId) {
        Empleado empleado = empleadoRepo.findById(empleadoId)
                .orElseThrow(() -> new RuntimeException("No se encontró el empleado con ID: " + empleadoId));

        return justificacionRepo.buscarAusenciasPorEmpleado(
                empleado.getId(),
                Ausencia.TipoDeAusencia.FALTA_SIN_AVISO
        );
    }
    public List<Ausencia> listarJustificacionesOrdenadas() {
        return justificacionRepo.findAllOrderByIdDesc();
    }




}