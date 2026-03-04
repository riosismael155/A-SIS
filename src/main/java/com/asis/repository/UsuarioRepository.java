package com.asis.repository;

import com.asis.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
@Repository

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    List<Usuario> findByUsernameContainingIgnoreCase(String username);

    // Cambia este método:
    List<Usuario> findByUsernameContainingIgnoreCaseOrEmpleado_DniContaining(String username, String dni);
}

