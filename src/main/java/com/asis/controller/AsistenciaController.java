package com.asis.controller;


import com.asis.model.Empleado;
import com.asis.model.LogCargaAsistencia;
import com.asis.model.dto.CargaAsistenciaDTO;
import com.asis.model.dto.EdicionHorarioDTO;
import com.asis.model.dto.ResumenAsistenciasDTO;
import com.asis.repository.EmpleadoRepository;
import com.asis.repository.LogCargaAsistenciaRepository;
import com.asis.repository.RegistroRepository;
import com.asis.service.EmpleadoService;
import com.asis.service.ExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/asistencias")
public class AsistenciaController {

    private final EmpleadoRepository empleadoRepo;

    private final RegistroRepository registroRepo;
    private final EmpleadoService empleadoService;
    private final LogCargaAsistenciaRepository cargaAsistRepo;
    private final ExcelService excelService;

    @GetMapping("/cargar")
    public String mostrarFormularioCarga(Model model) {
        LocalDate hoy = LocalDate.now();

        // Desde: 15 del mes pasado
        LocalDate desde = hoy.minusMonths(1).withDayOfMonth(21);

        // Hasta: 7 del mes actual
        LocalDate hasta = hoy.withDayOfMonth(20);

        model.addAttribute("desde", desde);
        model.addAttribute("hasta", hasta);

        return "asistencias/carga";
    }


    @PostMapping("/procesar")
    public String procesarArchivo(@ModelAttribute CargaAsistenciaDTO form, RedirectAttributes redirectAttrs) {
        excelService.cargarYGuardarAsistencias(form);  // Ya guarda todo, genera descripción y log

        String descripcion = excelService.generarDescripcion(form.getDesde(), form.getHasta());

        redirectAttrs.addFlashAttribute("mensaje",
                "Archivo procesado correctamente. Total: registros guardados.");

        // Redirigir al resumen con los datos de rango y descripción
        return "redirect:/asistencias/logs";
    }


    @GetMapping("/logs")
    public String listarLogs(Model model) {
        List<LogCargaAsistencia> logs = cargaAsistRepo
                .findAll(Sort.by(Sort.Direction.DESC, "desde"))
                .stream()
                .limit(12)
                .toList(); // Mostramos solo los últimos 12 logs

        model.addAttribute("logs", logs);
        return "asistencias/lista-logs";
    }

    @GetMapping("/resumen-por-log/{logId}")
    public String mostrarResumenTotalesPorLog(
            @PathVariable Long logId,
            @RequestParam(required = false) String tipoContrato,  // Recibir como String
            Model model) {

        Empleado.TipoContrato tipoEnum = null;
        if (tipoContrato != null && !tipoContrato.isEmpty()) {
            try {
                tipoEnum = Empleado.TipoContrato.valueOf(tipoContrato);
            } catch (IllegalArgumentException e) {
                // Manejar error si es necesario
            }
        }

        List<ResumenAsistenciasDTO> resumen = excelService.generarResumenTotalesPorLog(logId, tipoEnum);

        model.addAttribute("log", cargaAsistRepo.findById(logId).orElseThrow());
        model.addAttribute("resumen", resumen);
        model.addAttribute("tiposContrato", Empleado.TipoContrato.values());
        model.addAttribute("tipoContratoSeleccionado", tipoEnum);

        return "asistencias/resumen-log";  // Misma plantilla para todos los casos
    }
    @PostMapping("/logs/eliminar/{logId}")
    public String eliminarLog(@PathVariable Long logId, RedirectAttributes redirectAttrs) {
        cargaAsistRepo.findById(logId).ifPresentOrElse(log -> {
            // Eliminar registros de asistencia en el rango
            registroRepo.eliminarPorRangoFechas(log.getDesde(), log.getHasta());

            // Eliminar el log
            cargaAsistRepo.delete(log);

            redirectAttrs.addFlashAttribute("mensaje", "Log y registros de asistencia eliminados correctamente.");
        }, () -> {
            redirectAttrs.addFlashAttribute("error", "No se encontró el log con ID " + logId);
        });

        return "redirect:/asistencias/logs";
    }


    @GetMapping("/detalle")
    public String detalleAsistencia(
            @RequestParam(name = "dni", required = false) String dni,
            @RequestParam(name = "desde", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(name = "hasta", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Model model) {

        model.addAttribute("empleados", empleadoRepo.findAll());

        if (dni != null && desde != null && hasta != null) {
            Map<String, Object> detalleModel = excelService.generarDetalleEmpleadoView(dni, desde, hasta);

            // Agregar todos los atributos al modelo
            model.addAllAttributes(detalleModel);

            // Asegurar valores por defecto para evitar nulls
            if (!model.containsAttribute("detalle")) {
                model.addAttribute("detalle", Collections.emptyList());
            }
            if (!model.containsAttribute("totalHoras")) {
                model.addAttribute("totalHoras", 0.0);
            }
            if (!model.containsAttribute("totalNormales")) {
                model.addAttribute("totalNormales", 0.0);
            }
            if (!model.containsAttribute("totalExtras")) {
                model.addAttribute("totalExtras", 0.0);
            }
            if (!model.containsAttribute("totalFindeFeriado")) {
                model.addAttribute("totalFindeFeriado", 0.0);
            }
            if (!model.containsAttribute("totalAusencias")) {
                model.addAttribute("totalAusencias", 0);
            }
            if (!model.containsAttribute("totalLlegadasTarde")) {
                model.addAttribute("totalLlegadasTarde", 0);
            }
            if (!model.containsAttribute("diasTrabajados")) {
                model.addAttribute("diasTrabajados", 0);
            }
            if (!model.containsAttribute("minutosTardeTotales")) {
                model.addAttribute("minutosTardeTotales", 0);
            }

            model.addAttribute("dniSeleccionado", dni);
            // ✅ Aquí agregamos el booleano
            boolean esEmpleado = empleadoService.esUsuarioTipoEmpleado(dni);
            model.addAttribute("esEmpleado", esEmpleado);
        } else {
            LocalDate hoy = LocalDate.now();

            if (desde == null) {
                // 21 del mes anterior
                desde = hoy.minusMonths(1).withDayOfMonth(21);
            }
            if (hasta == null) {
                // 20 del mes actual
                hasta = hoy.withDayOfMonth(20);

                // Si hoy es antes del 20, usar 20 del mes anterior
                if (hoy.getDayOfMonth() < 20) {
                    hasta = hoy.minusMonths(1).withDayOfMonth(20);
                }
            }
            model.addAttribute("desde", desde);
            model.addAttribute("hasta", hasta);
        }

        return "asistencias/detalle-asistencias";
    }

    private void asegurarAtributosModelo(Model model, Map<String, Object> detalleModel) {
        // Lista de atributos que deben estar siempre presentes
        String[] atributosRequeridos = {
                "detalle", "empleado", "totalHoras", "totalNormales",
                "totalExtras", "totalFindeFeriado", "totalAusencias",
                "totalLlegadasTarde", "diasTrabajados", "minutosTardeTotales"
        };

        // Asegurar que cada atributo requerido tenga un valor por defecto si no está presente
        for (String atributo : atributosRequeridos) {
            if (!detalleModel.containsKey(atributo)) {
                switch (atributo) {
                    case "detalle":
                        model.addAttribute("detalle", Collections.emptyList());
                        break;
                    case "empleado":
                        // No podemos crear un empleado por defecto, pero al menos evitamos null
                        break;
                    case "totalHoras":
                    case "totalNormales":
                    case "totalExtras":
                    case "totalFindeFeriado":
                        model.addAttribute(atributo, 0.0);
                        break;
                    case "totalAusencias":
                    case "totalLlegadasTarde":
                    case "diasTrabajados":
                    case "minutosTardeTotales":
                        model.addAttribute(atributo, 0);
                        break;
                }
            }
        }
    }

    @GetMapping("/editar-horario")
    public String mostrarFormularioEdicion(
            @RequestParam String dni,
            @RequestParam LocalDate fecha,
            Model model) {

        // Obtenemos el DTO con los horarios basados en registros existentes
        EdicionHorarioDTO dto = excelService.prepararEdicionHorario(dni, fecha);

        // Verificamos si hay registros existentes
        boolean tieneRegistros = dto.getHoraEntrada1() != null || dto.getHoraEntrada2() != null;

        // Agregamos atributos al modelo
        model.addAttribute("edicionDTO", dto);
        model.addAttribute("tieneRegistros", tieneRegistros);

        return "asistencias/editar-horario";
    }

    @PostMapping("/guardar-horario")
    public String guardarHorarioEditado(
            @ModelAttribute EdicionHorarioDTO edicionDTO,
            RedirectAttributes redirectAttributes) {

        try {
            excelService.editarHorariosDia(edicionDTO);
            redirectAttributes.addFlashAttribute("success", "Horarios actualizados correctamente");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Error al actualizar horarios: " + e.getMessage());
        }

        // Redirecciona de vuelta al formulario de edición con los mismos parámetros
        return "redirect:/asistencias/editar-horario?dni=" + edicionDTO.getDni() +
                "&fecha=" + edicionDTO.getFecha().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}





