package com.asis.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ausencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Empleado empleado;

    private LocalDate desde;

    private LocalDate hasta;

    @Column(length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoDeAusencia tipoDeAusencia;

    @OneToMany(mappedBy = "justificacion", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<RegistroAsistencia> registrosGenerados = new ArrayList<>();



    public enum TipoDeAusencia {
        JUSTIFICADA("Justificada"),
        VACACIONES("Vacaciones"),
        NO_MARCO("No marcó"),
        FALTA_SIN_AVISO("Falta sin aviso"),
        FALTA_CON_AVISO("Falta con aviso");

        private final String descripcion;

        TipoDeAusencia(String descripcion) {
            this.descripcion = descripcion;
        }

        public String getDescripcion() {
            return descripcion;
        }
    }

}

