package com.asis.model;

import jakarta.persistence.*;
import lombok.Data;


@Data
@Entity
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(length = 20) // ajusta según los nombres de tus roles
    private Rol rol;

    @Column(nullable = false)
    private boolean activo;

    @OneToOne(mappedBy = "usuario", fetch = FetchType.LAZY)
    private Empleado empleado;



}