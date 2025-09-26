package com.asis.controller;

import com.asis.model.Empleado;
import com.asis.model.dto.BusquedaEmpleadoDTO;
import com.asis.model.dto.EmpleadoDTO;
import com.asis.repository.AreaRepository;
import com.asis.repository.EmpleadoRepository;
import com.asis.service.ExcelService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@RequestMapping("/empleados")
@PreAuthorize("hasAnyRole('ADMINISTRADOR')")

public class EmpleadoControler {
    private final EmpleadoRepository empleadoRepo;
    private final AreaRepository areaRepo;
    private final ExcelService excelService;

    @GetMapping("/buscar")
    @ResponseBody
    public List<BusquedaEmpleadoDTO> buscarEmpleados(@RequestParam String q) {
        List<Empleado> empleados = empleadoRepo.buscarPorNombreApellidoODni(q);
        return empleados.stream()
                .map(e -> new BusquedaEmpleadoDTO(e.getId(),e.getNombre(), e.getApellido(), e.getDni()))
                .collect(Collectors.toList());
    }
    @GetMapping("/cargar")
    public String mostrarFormularioEmpleado(Model model) {
        Empleado empleado = new Empleado();
        empleado.setHoraEntrada(LocalTime.of(7, 0));
        empleado.setHoraSalida(LocalTime.of(13, 0));
        empleado.setFlexMinutos(10);


        model.addAttribute("empleado", empleado);
        model.addAttribute("tiposContrato", Empleado.TipoContrato.values());
        model.addAttribute("areas", areaRepo.findAll()); // 👈 listado de áreas

        return "empleados/carga";
    }

    @PostMapping("/guardar")
    public String guardarEmpleado(@org.springframework.web.bind.annotation.ModelAttribute Empleado empleado,
                                  org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttrs) {
        empleado.setFlexMinutos(10); // siempre 5, backend manda
        empleadoRepo.save(empleado);
        redirectAttrs.addFlashAttribute("mensaje", "Empleado guardado correctamente.");
        return "redirect:/empleados/cargar";
    }

    @GetMapping("/modificar")
    public String mostrarFormularioEdicion(Model model) {
        model.addAttribute("empleado", new Empleado());  // Podés mandar un objeto vacío para bindear el formulario
        model.addAttribute("tiposContrato", Empleado.TipoContrato.values());
        model.addAttribute("areas", areaRepo.findAll());
        return "empleados/buscar-editar";
    }

    @PostMapping("/actualizar")
    public String actualizarEmpleado(@ModelAttribute Empleado empleado,
                                     RedirectAttributes redirectAttrs,
                                     Model model) {
        empleadoRepo.save(empleado);
        redirectAttrs.addFlashAttribute("mensaje", "Empleado actualizado correctamente.");

        // Para recargar el formulario con el empleado actualizado:
        model.addAttribute("empleado", empleado);
        model.addAttribute("tiposContrato", Empleado.TipoContrato.values());
        model.addAttribute("areas", areaRepo.findAll());

        return "empleados/buscar-editar";  // Mismo template para edición
    }

    @GetMapping("/{dni}")
    @ResponseBody
    public EmpleadoDTO obtenerEmpleadoPorDni(@PathVariable String dni) {
        Empleado empleado = empleadoRepo.findByDni(dni)
                .orElseThrow(() -> new IllegalArgumentException("Empleado no encontrado con DNI: " + dni));
        return new EmpleadoDTO(empleado);
    }

}

