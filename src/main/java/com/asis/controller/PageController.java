package com.asis.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'SUPERVISOR')")
    @GetMapping("/")
    public String index() {
        return "index"; // Thymeleaf buscará templates/index.html
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'SUPERVISOR')")
    @GetMapping("/manual")
    public String manual() {
        return "manual-usuario"; // Thymeleaf buscará templates/index.html
    }

    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'SUPERVISOR', 'EMPLEADO')")
    @GetMapping("/acceso-denegado")
    public String accesoDenegado() {
        return "acceso-denegado"; // nombre del template Thymeleaf
    }
}


