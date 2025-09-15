package com.asis.service;

import com.asis.model.Empleado;
import com.asis.repository.EmpleadoRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EmpleadoService {

    private final EmpleadoRepository empleadoRepository;

    public EmpleadoService(EmpleadoRepository empleadoRepository) {
        this.empleadoRepository = empleadoRepository;
    }

    public Empleado guardarEmpleado(Empleado empleado) {
        return empleadoRepository.save(empleado);
    }

    public boolean esUsuarioTipoEmpleado(String dni) {
        Optional<Empleado> empleadoOpt = empleadoRepository.findByDni(dni);
        if (empleadoOpt.isPresent()) {
            Empleado empleado = empleadoOpt.get();
            // Asumiendo que tienes una relación Empleado -> Usuario
            return empleado.getUsuario() != null && "EMPLEADO".equalsIgnoreCase(String.valueOf(empleado.getUsuario().getRol()));
        }
        return false;
    }
}
