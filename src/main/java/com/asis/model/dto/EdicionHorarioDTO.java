package com.asis.model.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;


@Data
public class EdicionHorarioDTO {
    private String dni;
    private LocalDate fecha;
    private List<RegistroDTO> registros = new ArrayList<>();
    private String motivoCambio;
}

