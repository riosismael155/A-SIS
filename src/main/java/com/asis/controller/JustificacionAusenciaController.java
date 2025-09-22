package com.asis.controller;

import com.asis.model.Ausencia;
import com.asis.repository.EmpleadoRepository;
import com.asis.service.JustificacionAusenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Controller
@RequestMapping("/justificaciones")
@RequiredArgsConstructor
public class JustificacionAusenciaController {

    private final EmpleadoRepository empleadoRepo;
    private final JustificacionAusenciaService justificacionService;

    @GetMapping("/cargar")
    public String verPantallaJustificaciones(Model model) {
        model.addAttribute("empleados", empleadoRepo.findAll());
        model.addAttribute("tiposAusencia", Ausencia.TipoDeAusencia.values());
        model.addAttribute("justificaciones", justificacionService.listarJustificaciones()); // agregamos la lista
        return "asistencias/justificaciones"; // misma vista para formulario y lista
    }

    @PostMapping("/guardar")
    public String justificarAusencia(@RequestParam("dni") String dni,
                                     @RequestParam("desde") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                     @RequestParam(value = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                     @RequestParam("descripcion") String descripcion,
                                     @RequestParam("tipoAusencia") Ausencia.TipoDeAusencia tipoAusencia) {

        if (hasta == null) hasta = desde;
        justificacionService.justificarAusencia(dni, desde, hasta, descripcion, tipoAusencia);

        // Redirigir a la misma página, con la pestaña de listado activa
        return "redirect:/justificaciones/cargar#listado";
    }

    // Eliminar una justificación
    @PostMapping("/eliminar/{id}")
    public String eliminarJustificacion(@PathVariable Long id) {
        justificacionService.eliminarJustificacion(id);
        // Redirigir a la misma página con pestaña de listado activa
        return "redirect:/justificaciones/cargar#listado";
    }
}
