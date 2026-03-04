package com.asis.controller;

import com.asis.service.HoraEnPlanillaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/horas-planilla")
public class HoraEnPlanillaController {

    private final HoraEnPlanillaService service;

    public HoraEnPlanillaController(HoraEnPlanillaService service) {
        this.service = service;
    }

    @GetMapping
    public void getAll() {
    }

    @GetMapping("/{id}")
    public void getById(@PathVariable Integer id) {
    }

    @PostMapping
    public void create() {
    }

    @PutMapping("/{id}")
    public void update(@PathVariable Integer id) {
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Integer id) {
    }
}
