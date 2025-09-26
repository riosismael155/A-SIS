package com.asis.service;

import com.asis.model.Empleado;
import com.asis.model.Usuario;
import com.asis.repository.EmpleadoRepository;
import com.asis.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final EmpleadoRepository empleadoRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          EmpleadoRepository empleadoRepository,
                          PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.empleadoRepository = empleadoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Usuario guardarUsuarioConEmpleado(Usuario usuario, Long empleadoId) {
        // Codificar la contraseña antes de guardar
        String encodedPassword = passwordEncoder.encode(usuario.getPassword());
        usuario.setPassword(encodedPassword);

        if (empleadoId != null) {
            Empleado empleado = empleadoRepository.findById(empleadoId)
                    .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));
            usuario.setEmpleado(empleado);
            empleado.setUsuario(usuario);
        }

        return usuarioRepository.save(usuario);
    }


}
