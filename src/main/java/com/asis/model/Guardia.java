package com.asis.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Guardia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate fecha;
    private int cant;

    @ManyToOne
    @JoinColumn(name = "semana_empleado_id", nullable = false)
    private SemanaEmpleado semanaEmpleado;

    @ManyToOne
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;

}

