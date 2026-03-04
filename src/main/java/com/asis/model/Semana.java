package com.asis.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
public class Semana {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDate desde;
    private LocalDate hasta;

    @ManyToOne
    @JoinColumn(name = "control_planilla_id", nullable = false)
    private ControlPlanilla controlPlanilla;

    @OneToMany(mappedBy = "semana", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SemanaEmpleado> empleados = new ArrayList<>();


}

