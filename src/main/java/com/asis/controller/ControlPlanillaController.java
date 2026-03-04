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
    public String crear() {

        ControlPlanilla cp = controlService.crearControlPlanilla(null);

        return "redirect:/control-planilla/" + cp.getId();
    }

    @GetMapping("/{id}")
    public String verPlanilla(
            @PathVariable("id") Long id,
            @RequestParam(value = "semanaId", required = false) Long semanaId,
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

        // Empleados de la semana actual
        List<SemanaEmpleado> empleadosSemana = semanaEmpleadoService.findBySemana(semana);
        model.addAttribute("empleadosSemana", empleadosSemana);

        // LOG: qué empleados están en la semana actual
        System.out.println("=== EMPLEADOS EN SEMANA ACTUAL ===");
        for (SemanaEmpleado se : empleadosSemana) {
            System.out.println("Semana actual - Empleado ID: " + se.getEmpleado().getId() +
                    " | Nombre: " + se.getEmpleado().getApellido() + ", " + se.getEmpleado().getNombre());
        }

        // AHORA PASAMOS LOS EMPLEADOS DE LA SEMANA al método
        TotalesMensualesDTO totalesMensuales = semanaEmpleadoService.calcularTotalesPeriodo(
                empleadosSemana,  // ← PASAMOS ESTA LISTA
                cp.getDesde(),
                cp.getHasta()
        );

        // LOG: qué totales mensuales tenemos para los empleados de la semana
        System.out.println("=== TOTALES MENSUALES (SOLO EMPLEADOS DE LA SEMANA) ===");
        for (Map.Entry<Long, TotalesEmpleadoDTO> entry :
                totalesMensuales.getTotalesPorEmpleado().entrySet()) {
            System.out.println("Empleado ID: " + entry.getKey() +
                    " | Nombre: " + entry.getValue().getNombreEmpleado() +
                    " | Horas50: " + entry.getValue().getTotalHorasExtra() +
                    " | Horas100: " + entry.getValue().getTotalFindeFeriado() +
                    " | Guardias: " + entry.getValue().getTotalGuardias());
        }

        model.addAttribute("totalesMensualesPorEmpleado", totalesMensuales.getTotalesPorEmpleado());
        // Si no necesitas totales generales, no los agregues
        // model.addAttribute("totalesGeneralesMensuales", totalesMensuales.getTotalesGenerales());

        // Empleados disponibles para agregar
        model.addAttribute("empleados", empleadoService.findAll());

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
            @RequestParam("semanaId") Long semanaId
    ) {
        semanaEmpleadoService.guardarCambios(form.getItems());

        Semana semana = semanaService.findById(semanaId);

        return "redirect:/control-planilla/"
                + semana.getControlPlanilla().getId()
                + "?semanaId=" + semanaId;
    }


    @PostMapping("/{id}/observaciones")
    public String guardarObservaciones(
            @PathVariable("id") Long id,
            @RequestParam("semanaId") Long semanaId,
            @RequestParam("observaciones") String observaciones,
            RedirectAttributes redirectAttributes) {

        try {
            controlService.actualizarObservaciones(id, observaciones);
            redirectAttributes.addFlashAttribute("mensaje", "Observaciones guardadas correctamente");
            redirectAttributes.addFlashAttribute("tipoMensaje", "success");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "Error al guardar observaciones");
            redirectAttributes.addFlashAttribute("tipoMensaje", "error");
        }

        return "redirect:/control-planilla/" + id + "?semanaId=" + semanaId;
    }




}

