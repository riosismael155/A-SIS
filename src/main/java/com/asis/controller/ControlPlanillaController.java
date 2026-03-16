package com.asis.controller;

import com.asis.model.ControlPlanilla;
import com.asis.model.Semana;
import com.asis.model.SemanaEmpleado;
import com.asis.model.dto.TotalesEmpleadoDTO;
import com.asis.model.dto.TotalesMensualesDTO;
import com.asis.model.wrapper.SemanaEmpleadoForm;
import com.asis.service.ControlPlanillaService;
import com.asis.service.EmpleadoService;
import com.asis.service.SemanaEmpleadoService;
import com.asis.service.SemanaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;


@Controller
@RequestMapping("/control-planilla")
public class ControlPlanillaController {

    private final ControlPlanillaService controlService;
    private final SemanaService semanaService;
    private final SemanaEmpleadoService semanaEmpleadoService;
    private final EmpleadoService empleadoService;


    public ControlPlanillaController(
            ControlPlanillaService controlService,
            SemanaService semanaService,
            SemanaEmpleadoService semanaEmpleadoService, EmpleadoService empleadoService
    ) {
        this.controlService = controlService;
        this.semanaService = semanaService;
        this.semanaEmpleadoService = semanaEmpleadoService;
        this.empleadoService = empleadoService;
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("controles", controlService.findAll());
        return "control-planilla/lista";
    }

    @PostMapping("/crear")
    public String crear(@RequestParam(required = false) String desde,
                        @RequestParam(required = false) String hasta,
                        @RequestParam(required = false) String observaciones) {

        ControlPlanilla cp;

        if (desde != null && hasta != null && !desde.isEmpty() && !hasta.isEmpty()) {
            // Convertir las fechas del formato String a LocalDate
            LocalDate fechaDesde = LocalDate.parse(desde);
            LocalDate fechaHasta = LocalDate.parse(hasta);
            cp = controlService.crearControlPlanilla(fechaDesde, fechaHasta, observaciones);
        } else {
            // Si no se proporcionan fechas, usar el comportamiento anterior (automático)
            cp = controlService.crearControlPlanilla(null);
        }

        return "redirect:/control-planilla/" + cp.getId();
    }

    @GetMapping("/{id}")
    public String verPlanilla(
            @PathVariable("id") Long id,
            @RequestParam(value = "semanaId", required = false) Long semanaId,
            @RequestParam(value = "scrollToBottom", required = false) Boolean scrollToBottom,
            Model model
    ) {
        ControlPlanilla cp = controlService.findById(id).orElseThrow();

        Semana semana = (semanaId == null)
                ? semanaService.obtenerSemanaInicial(cp)
                : semanaService.findById(semanaId);

        model.addAttribute("control", cp);
        model.addAttribute("semana", semana);
        model.addAttribute("semanaAnterior", semanaService.getAnterior(cp, semana));
        model.addAttribute("semanaSiguiente", semanaService.getSiguiente(cp, semana));

        // Empleados de la semana actual - ORDENADOS POR ID (más antiguos primero)
        List<SemanaEmpleado> empleadosSemana = semanaEmpleadoService.findBySemana(semana);

        // Ordenar por ID (asumiendo que IDs más grandes = más nuevos)
        if (empleadosSemana != null && !empleadosSemana.isEmpty()) {
            empleadosSemana.sort(Comparator.comparing(SemanaEmpleado::getId));
        }

        model.addAttribute("empleadosSemana", empleadosSemana);

        // Calcular totales mensuales
        TotalesMensualesDTO totalesMensuales = semanaEmpleadoService.calcularTotalesPeriodo(
                empleadosSemana,
                cp.getDesde(),
                cp.getHasta()
        );

        model.addAttribute("totalesMensualesPorEmpleado", totalesMensuales.getTotalesPorEmpleado());

        // Empleados disponibles para agregar
        model.addAttribute("empleados", empleadoService.findAll());

        // Agregar flag para scroll automático
        model.addAttribute("scrollToBottom", scrollToBottom != null ? scrollToBottom : false);

        return "control-planilla/detalle";
    }
    @GetMapping("/{id}/resumen")
    public String verResumenMensual(@PathVariable("id") Long id, Model model) {
        ControlPlanilla cp = controlService.findById(id).orElseThrow();

        // Reutilizamos el método que ya tenemos para calcular totales del período
        TotalesMensualesDTO totalesMensuales = semanaEmpleadoService.calcularTotalesResumen(
                cp.getDesde(), cp.getHasta()
        );

        model.addAttribute("control", cp);
        model.addAttribute("totalesMensuales", totalesMensuales);

        return "control-planilla/resumen";
    }

    @PostMapping("/guardar")
    public String guardarCambios(
            @ModelAttribute SemanaEmpleadoForm form,
            @RequestParam("semanaId") Long semanaId,
            @RequestParam(value = "scrollToBottom", required = false) Boolean scrollToBottom,
            RedirectAttributes redirectAttributes
    ) {
        try {
            semanaEmpleadoService.guardarCambios(form.getItems());
            redirectAttributes.addFlashAttribute("mensaje", "Cambios guardados correctamente");
            redirectAttributes.addFlashAttribute("tipoMensaje", "success");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "Error al guardar cambios: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tipoMensaje", "error");
        }

        Semana semana = semanaService.findById(semanaId);

        // Agregar atributo para scroll si viene en la petición
        if (scrollToBottom != null && scrollToBottom) {
            redirectAttributes.addAttribute("scrollToBottom", true);
        }

        return "redirect:/control-planilla/"
                + semana.getControlPlanilla().getId()
                + "?semanaId=" + semanaId;
    }

    @PostMapping("/guardar-y-agregar")
    public String guardarYAgregar(
            @ModelAttribute SemanaEmpleadoForm form,
            @RequestParam("semanaId") Long semanaId,
            @RequestParam("empleadoId") Long empleadoId,
            @RequestParam(value = "scrollToBottom", required = false) Boolean scrollToBottom,
            RedirectAttributes redirectAttributes) {

        try {
            // Primero guardar los cambios de horas
            semanaEmpleadoService.guardarCambios(form.getItems());

            // Luego agregar el nuevo empleado
            semanaEmpleadoService.agregarEmpleadoASemana(semanaId, empleadoId);

            redirectAttributes.addFlashAttribute("mensaje", "Cambios guardados y empleado agregado correctamente");
            redirectAttributes.addFlashAttribute("tipoMensaje", "success");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "Error: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tipoMensaje", "error");
        }

        Semana semana = semanaService.findById(semanaId);

        if (scrollToBottom != null && scrollToBottom) {
            redirectAttributes.addAttribute("scrollToBottom", true);
        }

        return "redirect:/control-planilla/"
                + semana.getControlPlanilla().getId()
                + "?semanaId=" + semanaId;
    }


    @PostMapping("/{id}/observaciones")
    public String guardarObservaciones(
            @PathVariable("id") Long id,
            @RequestParam("semanaId") Long semanaId,
            @RequestParam("observaciones") String observaciones,
            @RequestParam(value = "scrollToBottom", required = false) Boolean scrollToBottom,
            RedirectAttributes redirectAttributes) {

        try {
            controlService.actualizarObservaciones(id, observaciones);
            redirectAttributes.addFlashAttribute("mensaje", "Observaciones guardadas correctamente");
            redirectAttributes.addFlashAttribute("tipoMensaje", "success");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "Error al guardar observaciones");
            redirectAttributes.addFlashAttribute("tipoMensaje", "error");
        }

        // Agregar atributo para scroll si viene en la petición
        if (scrollToBottom != null && scrollToBottom) {
            redirectAttributes.addAttribute("scrollToBottom", true);
        }

        return "redirect:/control-planilla/" + id + "?semanaId=" + semanaId;
    }




}

