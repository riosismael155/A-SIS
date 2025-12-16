package com.asis.controller;

import com.asis.model.Empleado;
import com.asis.model.RegistroAsistencia;
import com.asis.model.Rol;
import com.asis.model.Usuario;
import com.asis.repository.EmpleadoRepository;
import com.asis.repository.RegistroRepository;
import com.asis.repository.UsuarioRepository;
import com.asis.service.UsuarioService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;

@Controller
public class LoginController {

    @Autowired
    private UsuarioRepository usuarioRepo;

    @Autowired
    private EmpleadoRepository empleadoRepo;

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping("/login")
    public String mostrarLogin() {
        return "login";
    }


    @GetMapping("/post-login")
    public String postLogin(Authentication authentication,
                            HttpSession session,
                            RedirectAttributes redirectAttributes) {

        String username = authentication.getName();
        Usuario usuario = usuarioRepo.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        if (usuario.getRol() == Rol.EMPLEADO) {
            Empleado empleado = empleadoRepo.findByUsuarioId(usuario.getId())
                    .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

            // Guardamos el ID del empleado en sesión para el próximo paso
            session.setAttribute("empleadoId", empleado.getId());
            return "captura-foto"; // Nueva vista para capturar foto
        }

        return switch (usuario.getRol()) {
            default -> "redirect:/";
        };
    }

    @PostMapping("/registrar-asistencia")
    public String registrarAsistencia(
            @RequestParam("fotoBase64") String fotoBase64,
            @SessionAttribute("empleadoId") Long empleadoId,
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        Empleado empleado = empleadoRepo.findById(empleadoId)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        // Convertir base64 a byte[]
        String base64Image = fotoBase64.split(",")[1];
        byte[] imageBytes = Base64.getDecoder().decode(base64Image);

        // 👉 delegar al service (transacción + lock + cooldown)
        usuarioService.registrarMarcaConFoto(empleado, imageBytes);

        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?marca=ok";
    }

}
