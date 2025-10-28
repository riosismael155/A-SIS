package com.asis.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BusquedaEmpleadoDTO {
    private Long id;
    private String nombre;
    private String apellido;
    private String dni;

}



