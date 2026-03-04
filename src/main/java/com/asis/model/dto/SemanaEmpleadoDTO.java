package com.asis.model.dto;

import lombok.Data;

@Data
public class SemanaEmpleadoDTO {

    private Long id;
    private double totalHorasExtra;
    private double totalFindeFeriado;
    private int totalGuardias;

}

