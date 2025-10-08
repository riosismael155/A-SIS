package com.asis.controller;

import com.asis.model.Ausencia;
import com.asis.repository.EmpleadoRepository;
import com.asis.service.JustificacionAusenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/justificaciones")
@PreAuthorize("hasAnyRole('ADMINISTRADOR')")
@RequiredArgsConstructor
public class JustificacionAusenciaController {

    private final EmpleadoRepository empleadoRepo;
    private final JustificacionAusenciaService justificacionService;

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'SUPERVISOR')")
    @GetMapping("/cargar")
    public String verPantallaJustificaciones(Model model) {
        model.addAttribute("empleados", empleadoRepo.findAll());

        // Filtramos para que NO aparezca FALTA_SIN_AVISO
        List<Ausencia.TipoDeAusencia> tiposAusencia = Arrays.stream(Ausencia.TipoDeAusencia.values())
                .filter(t -> t != Ausencia.TipoDeAusencia.FALTA_SIN_AVISO)
                .toList();
        model.addAttribute("tiposAusencia", tiposAusencia);

        model.addAttribute("justificaciones", justificacionService.listarJustificaciones());
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

    @PostMapping("/eliminar/{id}")
    public String eliminarJustificacion(@PathVariable Long id) {
        justificacionService.eliminarJustificacion(id);
        // Redirigir a la misma página con pestaña de listado activa
        return "redirect:/justificaciones/cargar#listado";
    }
}
