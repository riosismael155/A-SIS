package com.asis.model;


import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistroAsistencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "empleado_dni")
    private Empleado empleado;

    private LocalDate fecha;
    private LocalTime hora;
    private Integer ordenDia;

    @Enumerated(EnumType.STRING)
    private TipoHora tipoHora;

    @ManyToOne
    @JoinColumn(name = "ausencia_id")
    private Ausencia justificacion;

    private String motivoCambio;

    private String observacion;
    private String tipo;

    @Lob
    @Column(columnDefinition = "LONGBLOB")
    private byte[] foto;


    public enum TipoHora {
        NORMAL,
        EXTRA,
        FIN_SEMANA

    }


}

