package com.asis.controller;

import com.asis.model.Ausencia;
import com.asis.model.dto.AusenciaDTO;
import com.asis.repository.EmpleadoRepository;
import com.asis.service.JustificacionAusenciaService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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

        List<Ausencia.TipoDeAusencia> tiposAusencia = Arrays.stream(Ausencia.TipoDeAusencia.values())
                .filter(t -> t != Ausencia.TipoDeAusencia.FALTA_SIN_AVISO)
                .toList();
        model.addAttribute("tiposAusencia", tiposAusencia);

        // 🔽 Usamos el nuevo método ordenado
        model.addAttribute("justificaciones", justificacionService.listarJustificacionesOrdenadas());

        return "asistencias/justificaciones";
    }

    @PostMapping("/guardar")
    public String justificarAusencia(@RequestParam("dni") String dni,
                                     @RequestParam("desde") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
                                     @RequestParam(value = "hasta", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
                                     @RequestParam("descripcion") String descripcion,
                                     @RequestParam("tipoAusencia") Ausencia.TipoDeAusencia tipoAusencia,
                                     RedirectAttributes redirectAttributes) {

        try {
            if (hasta == null) hasta = desde;
            justificacionService.justificarAusencia(dni, desde, hasta, descripcion, tipoAusencia);

            // Agregar atributo para mostrar mensaje de éxito
            redirectAttributes.addFlashAttribute("showSuccess", true);

        } catch (Exception e) {
            // Agregar atributo para mostrar mensaje de error
            redirectAttributes.addFlashAttribute("showError", true);
        }

        // Redirigir a la misma página, con la pestaña de listado activa
        return "redirect:/justificaciones/cargar#listado";
    }

    @PostMapping("/eliminar/{id}")
    public String eliminarJustificacion(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            justificacionService.eliminarJustificacion(id);

            // Agregar atributo para mostrar mensaje de éxito
            redirectAttributes.addFlashAttribute("showSuccess", true);

        } catch (Exception e) {
            // Agregar atributo para mostrar mensaje de error
            redirectAttributes.addFlashAttribute("showError", true);
        }

        // Redirigir a la misma página con pestaña de listado activa
        return "redirect:/justificaciones/cargar#listado";
    }


    @GetMapping("/buscar-por-empleado")
    @ResponseBody
    public ResponseEntity<List<AusenciaDTO>> buscarAusenciasPorEmpleado(@RequestParam Long empleadoId) {
        if (empleadoId == null) {
            return ResponseEntity.badRequest().build();
        }

        List<AusenciaDTO> ausencias = justificacionService.buscarAusenciasJustificadasPorEmpleadoId(empleadoId)
                .stream()
                .map(AusenciaDTO::fromEntity)
                .toList();

        return ResponseEntity.ok(ausencias);
    }


}
