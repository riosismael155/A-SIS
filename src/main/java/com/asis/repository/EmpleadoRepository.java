package com.asis.repository;

import com.asis.model.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {

    Optional<Empleado> findByDni(String dni);


    List<Empleado> findByTipoContrato(Empleado.TipoContrato tipoContrato);

    Optional<Empleado> findByUsuarioId(Long usuarioId);

    @Query("SELECT e FROM Empleado e WHERE " +
            "LOWER(e.nombre) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(e.apellido) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "CAST(e.dni AS string) LIKE CONCAT('%', :q, '%')")
    List<Empleado> buscarPorNombreApellidoODni(@Param("q") String q);


    boolean existsByDni(String dni);

    @Query("SELECT e FROM Empleado e WHERE e.usuario.username = :username")
    Empleado findByUsuario(@Param("username") String username);

    @Query("SELECT e FROM Empleado e WHERE e.usuario IS NULL")
    List<Empleado> findEmpleadosSinUsuario();

}
