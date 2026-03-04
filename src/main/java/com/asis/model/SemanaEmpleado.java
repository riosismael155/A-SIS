package com.asis.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SemanaEmpleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "semana_id", nullable = false)
    private Semana semana;

    @ManyToOne
    @JoinColumn(name = "empleado_id", nullable = false)
    private Empleado empleado;

    @OneToMany(mappedBy = "semanaEmpleado", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Guardia> guardias = new ArrayList<>();

    @OneToMany(mappedBy = "semanaEmpleado", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<HoraEnPlanilla> horasEnPlanilla = new ArrayList<>();

    private Integer totalGuardias;
    private double totalHorasExtra;
    private double totalFindeFeriado;
}
