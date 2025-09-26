package com.asis;

import com.asis.model.Empleado;
import com.asis.model.Rol;
import com.asis.model.Usuario;
import com.asis.repository.EmpleadoRepository;
import com.asis.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalTime;

@Configuration
public class DataLoader {

    @Bean
    public CommandLineRunner cargarDatosIniciales(UsuarioRepository usuarioRepo,
                                                  EmpleadoRepository empleadoRepo,
                                                  PasswordEncoder passwordEncoder) {
        return args -> {


            // 2. Crear usuario empleado con empleado asociado
            usuarioRepo.findByUsername("empleado").orElseGet(() -> {
                Usuario usuarioEmpleado = new Usuario();
                usuarioEmpleado.setUsername("empleado");
                usuarioEmpleado.setPassword(passwordEncoder.encode("1111"));
                usuarioEmpleado.setRol(Rol.EMPLEADO);
                usuarioEmpleado.setActivo(true);

                Usuario savedUser = usuarioRepo.save(usuarioEmpleado);

                // Verificar si el empleado ya existe
                if (!empleadoRepo.existsByDni("1111")) {
                    Empleado empleado = new Empleado();
                    empleado.setDni("1111");
                    empleado.setNombre("Empleado Prueba");
                    empleado.setApellido("Demo"); // Añadir apellido
                    empleado.setHoraEntrada(LocalTime.of(8, 0)); // Horario por defecto
                    empleado.setHoraSalida(LocalTime.of(16, 0));
                    empleado.setUsuario(savedUser);

                    empleadoRepo.save(empleado);
                }

                return savedUser;
            });

        };
    }
}