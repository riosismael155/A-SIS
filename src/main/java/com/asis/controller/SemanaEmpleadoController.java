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
            @RequestParam(value = "scrollToBottom", required = false) Boolean scrollToBottom,
            RedirectAttributes redirectAttributes) {

        try {
            semanaEmpleadoService.agregarEmpleadoASemana(semanaId, empleadoId);
            redirectAttributes.addFlashAttribute("mensaje", "Empleado agregado correctamente");
            redirectAttributes.addFlashAttribute("tipoMensaje", "success");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("mensaje", "Error al agregar empleado: " + e.getMessage());
            redirectAttributes.addFlashAttribute("tipoMensaje", "error");
        }

        Semana semana = semanaService.findById(semanaId);
        ControlPlanilla controlPlanilla = semana.getControlPlanilla();

        // Agregar atributo para scroll si viene en la petición
        if (scrollToBottom != null && scrollToBottom) {
            redirectAttributes.addAttribute("scrollToBottom", true);
        }

        return "redirect:/control-planilla/"
                + controlPlanilla.getId()
                + "?semanaId=" + semanaId;
    }

    @PostMapping("/agregar-con-guardado")
    public String agregarEmpleadoConGuardado(
            @ModelAttribute SemanaEmpleadoForm form,
            @RequestParam("semanaId") Long semanaId,
            @RequestParam("empleadoId") Long empleadoId,
            @RequestParam(value = "scrollToBottom", required = false) Boolean scrollToBottom,
            RedirectAttributes redirectAttributes
    ) {
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


}
