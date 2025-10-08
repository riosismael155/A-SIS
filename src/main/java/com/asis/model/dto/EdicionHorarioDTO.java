package com.asis.model.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class EdicionHorarioDTO {
    private String dni;
    private LocalDate fecha;

//    // Primer horario
//    private LocalTime horaEntrada1;
//    private LocalTime horaSalida1;
//
//    // Segundo horario
//    private LocalTime horaEntrada2;
//    private LocalTime horaSalida2;

//  Listas dinámicas de horarios
    private List<LocalTime> entradas = new ArrayList<>();
    private List<LocalTime> salidas = new ArrayList<>();
    private String motivoCambio;

    public void agregarPar(LocalTime entrada, LocalTime salida) {
        entradas.add(entrada);
        salidas.add(salida);
    }

}