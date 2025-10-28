package com.asis.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index"; // Thymeleaf buscará templates/index.html
    }

    @GetMapping("/manual")
    public String manual() {
        return "manual-usuario"; // Thymeleaf buscará templates/index.html
    }


    @GetMapping("/acceso-denegado")
    public String accesoDenegado() {
        return "acceso-denegado"; // nombre del template Thymeleaf
    }
}


