package com.asis.service;

import com.asis.model.Empleado;
import com.asis.model.RegistroAsistencia;
import com.asis.model.Usuario;
import com.asis.repository.EmpleadoRepository;
import com.asis.repository.RegistroRepository;
import com.asis.repository.UsuarioRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final EmpleadoRepository empleadoRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistroRepository registroRepo;

    public UsuarioService(UsuarioRepository usuarioRepository,
                          EmpleadoRepository empleadoRepository,
                          PasswordEncoder passwordEncoder, RegistroRepository registroRepo) {
        this.usuarioRepository = usuarioRepository;
        this.empleadoRepository = empleadoRepository;
        this.passwordEncoder = passwordEncoder;
        this.registroRepo = registroRepo;
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

    @Transactional
    public void registrarMarcaConFoto(Empleado empleado, byte[] foto) {

        List<RegistroAsistencia> marcas =
                registroRepo.findUltimaMarcaConLock(empleado.getId());

        LocalDateTime ahora = LocalDateTime.now().minusHours(3);
        int COOLDOWN_MINUTOS = 10;

        if (!marcas.isEmpty()) {
            RegistroAsistencia ultima = marcas.get(0);

            LocalDateTime ultimaFechaHora =
                    LocalDateTime.of(ultima.getFecha(), ultima.getHora());

            long minutos = ChronoUnit.MINUTES.between(ultimaFechaHora, ahora);

            if (minutos < COOLDOWN_MINUTOS) {
                return; // 👻 salida silenciosa
            }
        }

        LocalDate hoy = ahora.toLocalDate();
        LocalTime hora = ahora.toLocalTime();

        Integer ultimoOrden =
                registroRepo.findMaxOrdenDiaByEmpleadoAndFecha(empleado.getId(), hoy);

        int nuevoOrden = (ultimoOrden == null) ? 1 : ultimoOrden + 1;

        RegistroAsistencia registro = new RegistroAsistencia();
        registro.setFecha(hoy);
        registro.setHora(hora);
        registro.setOrdenDia(nuevoOrden);
        registro.setEmpleado(empleado);
        registro.setTipo(nuevoOrden % 2 == 1 ? "ENTRADA" : "SALIDA");
        registro.setFoto(foto);

        registroRepo.save(registro);
    }

}
