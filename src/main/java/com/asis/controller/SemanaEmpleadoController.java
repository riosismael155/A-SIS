package com.asis.controller;

import com.asis.model.ControlPlanilla;
import com.asis.model.Empleado;
import com.asis.model.Semana;
import com.asis.model.wrapper.SemanaEmpleadoForm;
import com.asis.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/semana-empleado")
public class SemanaEmpleadoController {

    private final SemanaService semanaService;
    private final EmpleadoService empleadoService;
    private final SemanaEmpleadoService semanaEmpleadoService;
    private final HoraEnPlanillaService horaService;
    private final GuardiaService guardiaService;

    public SemanaEmpleadoController(
            SemanaService semanaService,
            EmpleadoService empleadoService,
            SemanaEmpleadoService semanaEmpleadoService,
            HoraEnPlanillaService horaService,
            GuardiaService guardiaService
    ) {
        this.semanaService = semanaService;
        this.empleadoService = empleadoService;
        this.semanaEmpleadoService = semanaEmpleadoService;
        this.horaService = horaService;
        this.guardiaService = guardiaService;
    }

    @PostMapping("/agregar")
    public String agregarEmpleadoASemana(
            @RequestParam("semanaId") Long semanaId,
            @RequestParam("empleadoId") Long empleadoId,
            RedirectAttributes redirectAttributes) {

        try {
            semanaEmpleadoService.agregarEmpleadoASemana(semanaId, empleadoId);
            redirectAttributes.addFlashAttribute("mensaje", "Empleado agregado correctamente");
            redirectAttributes.addFlashAttribute("tipoMensaje", "success");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "Error al agregar empleado: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tipoMensaje", "error");
        }

        // Obtener la semana y su control de planilla asociado
        Semana semana = semanaService.findById(semanaId);
        ControlPlanilla controlPlanilla = semana.getControlPlanilla();

        return "redirect:/control-planilla/" + controlPlanilla.getId() + "?semanaId=" + semanaId;
    }






}
