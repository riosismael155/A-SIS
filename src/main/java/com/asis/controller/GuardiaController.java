package com.asis.controller;

import com.asis.service.GuardiaService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/guardias")
public class GuardiaController {

    private final GuardiaService service;

    public GuardiaController(GuardiaService service) {
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
