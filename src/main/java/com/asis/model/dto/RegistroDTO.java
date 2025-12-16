package com.asis.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RegistroDTO {
    private Long id;  // ID del registro existente, null si es nuevo
    private LocalTime hora;
    private String tipo;  // "ENTRADA" o "SALIDA"
}
