package com.asis.service;

import com.asis.model.Ausencia;
import com.asis.model.Empleado;
import com.asis.model.RegistroAsistencia;
import com.asis.repository.EmpleadoRepository;
import com.asis.repository.JustificacionAusenciaRepository;
import com.asis.repository.RegistroRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
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
                    entrada.setHora(empleado.getHoraEntrada());
                    entrada.setOrdenDia(1);
                    entrada.setTipoHora(RegistroAsistencia.TipoHora.NORMAL);
                    entrada.setJustificacion(justificacion);

                    // Crear registro de salida
                    RegistroAsistencia salida = new RegistroAsistencia();
                    salida.setEmpleado(empleado);
                    salida.setFecha(actual);
                    salida.setHora(empleado.getHoraSalida());
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