package com.asis.controller;

import com.asis.model.Feriado;
import com.asis.service.FeriadoService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequiredArgsConstructor
@RequestMapping("/feriados")
public class FeriadoController {

    private final FeriadoService feriadoService;

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'SUPERVISOR')")
    @GetMapping
    public String listarFeriados(Model model) {
        model.addAttribute("feriados", feriadoService.listarFeriados());
        model.addAttribute("feriadoNuevo", new Feriado());
        return "feriados/lista";
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR')")
    @PostMapping("/guardar")
    public String guardarFeriado(@ModelAttribute("feriadoNuevo") Feriado feriado) {
        feriadoService.agregarFeriado(feriado);
        return "redirect:/feriados";
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR')")
    @PostMapping("/eliminar/{id}")
    public String eliminarFeriado(@PathVariable Long id) {
        feriadoService.eliminarFeriado(id);
        return "redirect:/feriados";
    }
}