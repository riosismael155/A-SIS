package com.asis.service;

import com.asis.model.Empleado;
import com.asis.model.Guardia;
import com.asis.model.Semana;
import com.asis.model.SemanaEmpleado;
import com.asis.model.dto.SemanaEmpleadoDTO;
import com.asis.model.dto.TotalesEmpleadoDTO;
import com.asis.model.dto.TotalesMensualesDTO;
import com.asis.repository.SemanaEmpleadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class SemanaEmpleadoService {

    private final SemanaEmpleadoRepository repo;
    private final SemanaService semanaService;
    private final EmpleadoService empleadoService;

    public SemanaEmpleadoService(SemanaEmpleadoRepository repo, SemanaService semanaService, EmpleadoService empleadoService) {
        this.repo = repo;
        this.semanaService = semanaService;
        this.empleadoService = empleadoService;
    }

    public void agregarEmpleadoASemana(Long semanaId, Long empleadoId) {

        Semana semana = semanaService.findById(semanaId);
        Empleado empleado = empleadoService.findById(empleadoId);

        boolean existe = repo.existsBySemanaIdAndEmpleadoId(
                semana.getId(),
                empleado.getId()
        );
        if (existe) return; // no duplicados

        SemanaEmpleado se = new SemanaEmpleado();
        se.setSemana(semana);
        se.setEmpleado(empleado);

        // Inicializar totalGuardias en 0 (importante!)
        se.setTotalGuardias(0);

        // Los double ya se inicializan en 0 por defecto, pero por claridad:
        se.setTotalHorasExtra(0.0);
        se.setTotalFindeFeriado(0.0);

        repo.save(se);
    }
    @Transactional
    public void guardarCambios(List<SemanaEmpleadoDTO> items) {

        for (SemanaEmpleadoDTO dto : items) {

            SemanaEmpleado se = repo.findById(dto.getId())
                    .orElseThrow();

            se.setTotalHorasExtra(dto.getTotalHorasExtra());
            se.setTotalFindeFeriado(dto.getTotalFindeFeriado());
            se.setTotalGuardias(dto.getTotalGuardias());

            repo.save(se);
        }
    }


    public List<SemanaEmpleado> obtenerEmpleadosDeSemana(Semana semana) {
        return repo.findBySemanaIdOrderByIdAsc(semana.getId());
    }

    public Optional<SemanaEmpleado> findById(Long id) {
        return repo.findById(id);
    }

    public List<SemanaEmpleado> findBySemana(Semana semana) {
        return repo.findBySemanaIdOrderByIdAsc(semana.getId());
    }
    public List<SemanaEmpleado> findAllByPeriodo(LocalDate desde, LocalDate hasta) {
        return repo.findAllByPeriodo(desde, hasta);
    }

    public TotalesMensualesDTO calcularTotalesPeriodo(
            List<SemanaEmpleado> empleadosSemana,  // Empleados de la semana actual
            LocalDate desde,
            LocalDate hasta) {

        // 1. Crear Map con TODOS los empleados de la semana (inicializados en 0)
        Map<Long, TotalesEmpleadoDTO> totalesPorEmpleado = new HashMap<>();

        // Inicializar con los empleados de la semana actual
        for (SemanaEmpleado se : empleadosSemana) {
            Long empId = se.getEmpleado().getId();
            TotalesEmpleadoDTO dto = new TotalesEmpleadoDTO();
            dto.setEmpleadoId(empId);
            dto.setNombreEmpleado(se.getEmpleado().getApellido() + ", " + se.getEmpleado().getNombre());
            dto.setTotalHorasExtra(0.0);
            dto.setTotalFindeFeriado(0.0);
            dto.setTotalGuardias(0);
            totalesPorEmpleado.put(empId, dto);
        }

        // 2. Obtener TODOS los registros del período
        List<SemanaEmpleado> todosRegistros = repo.findAllByPeriodo(desde, hasta);

        // 3. Sumar los valores SOLO para los empleados que están en la semana
        for (SemanaEmpleado reg : todosRegistros) {
            Long empleadoId = reg.getEmpleado().getId();
            TotalesEmpleadoDTO totalesEmpleado = totalesPorEmpleado.get(empleadoId);

            if (totalesEmpleado != null) { // Solo si el empleado está en la semana actual
                // Sumar horas extra (double primitivo)
                totalesEmpleado.setTotalHorasExtra(
                        totalesEmpleado.getTotalHorasExtra() + reg.getTotalHorasExtra()
                );

                // Sumar finde feriado
                totalesEmpleado.setTotalFindeFeriado(
                        totalesEmpleado.getTotalFindeFeriado() + reg.getTotalFindeFeriado()
                );

                // Guardias (puede ser null)
                if (reg.getTotalGuardias() != null) {
                    totalesEmpleado.setTotalGuardias(
                            totalesEmpleado.getTotalGuardias() + reg.getTotalGuardias()
                    );
                }
            }
        }

        // 4. Calcular totales generales (opcional, si los necesitas)
        TotalesEmpleadoDTO totalesGenerales = new TotalesEmpleadoDTO();
        for (TotalesEmpleadoDTO totalesEmpleado : totalesPorEmpleado.values()) {
            totalesGenerales.setTotalHorasExtra(
                    totalesGenerales.getTotalHorasExtra() + totalesEmpleado.getTotalHorasExtra()
            );
            totalesGenerales.setTotalFindeFeriado(
                    totalesGenerales.getTotalFindeFeriado() + totalesEmpleado.getTotalFindeFeriado()
            );
            totalesGenerales.setTotalGuardias(
                    totalesGenerales.getTotalGuardias() + totalesEmpleado.getTotalGuardias()
            );
        }

        TotalesMensualesDTO resultado = new TotalesMensualesDTO();
        resultado.setTotalesPorEmpleado(totalesPorEmpleado);
        resultado.setTotalesGenerales(totalesGenerales);

        return resultado;
    }

    public TotalesMensualesDTO calcularTotalesResumen(LocalDate desde, LocalDate hasta) {

        // Obtener todos los registros del período
        List<SemanaEmpleado> todosRegistros = repo.findAllByPeriodo(desde, hasta);

        Map<Long, TotalesEmpleadoDTO> totalesPorEmpleado = new HashMap<>();
        Map<String, List<TotalesEmpleadoDTO>> empleadosPorTipoContrato = new HashMap<>();
        Map<String, TotalesEmpleadoDTO> subtotalesPorTipo = new HashMap<>();
        TotalesEmpleadoDTO totalesGenerales = new TotalesEmpleadoDTO();

        for (SemanaEmpleado reg : todosRegistros) {
            Long empleadoId = reg.getEmpleado().getId();

            TotalesEmpleadoDTO totalesEmpleado = totalesPorEmpleado.get(empleadoId);
            if (totalesEmpleado == null) {
                totalesEmpleado = new TotalesEmpleadoDTO();
                totalesEmpleado.setEmpleadoId(empleadoId);
                totalesEmpleado.setNombreEmpleado(
                        reg.getEmpleado().getApellido() + ", " + reg.getEmpleado().getNombre()
                );

                // Obtener el tipo de contrato del empleado (usando el enum)
                Empleado.TipoContrato tipoContrato = reg.getEmpleado().getTipoContrato();
                String tipoContratoLabel = tipoContrato != null ? tipoContrato.getLabel() : "Sin especificar";
                totalesEmpleado.setTipoContrato(tipoContratoLabel);
                totalesEmpleado.setTipoContratoEnum(tipoContrato); // Si quieres guardar el enum también

                totalesPorEmpleado.put(empleadoId, totalesEmpleado);

                // Agrupar por tipo de contrato (usando la etiqueta)
                empleadosPorTipoContrato
                        .computeIfAbsent(tipoContratoLabel, k -> new ArrayList<>())
                        .add(totalesEmpleado);
            }

            totalesEmpleado.setTotalHorasExtra(
                    totalesEmpleado.getTotalHorasExtra() + reg.getTotalHorasExtra()
            );
            totalesEmpleado.setTotalFindeFeriado(
                    totalesEmpleado.getTotalFindeFeriado() + reg.getTotalFindeFeriado()
            );

            if (reg.getTotalGuardias() != null) {
                totalesEmpleado.setTotalGuardias(
                        totalesEmpleado.getTotalGuardias() + reg.getTotalGuardias()
                );
            }
        }

        // Calcular subtotales por tipo de contrato y totales generales
        for (Map.Entry<String, List<TotalesEmpleadoDTO>> entry : empleadosPorTipoContrato.entrySet()) {
            TotalesEmpleadoDTO subtotal = new TotalesEmpleadoDTO();

            for (TotalesEmpleadoDTO emp : entry.getValue()) {
                subtotal.setTotalHorasExtra(
                        subtotal.getTotalHorasExtra() + emp.getTotalHorasExtra()
                );
                subtotal.setTotalFindeFeriado(
                        subtotal.getTotalFindeFeriado() + emp.getTotalFindeFeriado()
                );
                subtotal.setTotalGuardias(
                        subtotal.getTotalGuardias() + emp.getTotalGuardias()
                );
            }

            subtotalesPorTipo.put(entry.getKey(), subtotal);

            // Acumular totales generales
            totalesGenerales.setTotalHorasExtra(
                    totalesGenerales.getTotalHorasExtra() + subtotal.getTotalHorasExtra()
            );
            totalesGenerales.setTotalFindeFeriado(
                    totalesGenerales.getTotalFindeFeriado() + subtotal.getTotalFindeFeriado()
            );
            totalesGenerales.setTotalGuardias(
                    totalesGenerales.getTotalGuardias() + subtotal.getTotalGuardias()
            );
        }

        TotalesMensualesDTO resultado = new TotalesMensualesDTO();
        resultado.setTotalesPorEmpleado(totalesPorEmpleado);
        resultado.setEmpleadosPorTipoContrato(empleadosPorTipoContrato);
        resultado.setSubtotalesPorTipo(subtotalesPorTipo);
        resultado.setTotalesGenerales(totalesGenerales);

        return resultado;
    }}
