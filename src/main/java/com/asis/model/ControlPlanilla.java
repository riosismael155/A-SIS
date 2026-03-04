package com.asis.model;

import jakarta.persistence.*;
import lombok.Data;


import java.time.LocalDate;
import java.util.List;


@Data
@Entity
public class ControlPlanilla {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre;
    private String observaciones;

    private LocalDate desde;
    private LocalDate hasta;



    @OneToMany(
            mappedBy = "controlPlanilla",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Semana> semanas;
}
