package com.asis.model.dto;

import com.asis.model.Empleado;
import lombok.Data;

@Data
public class TotalesEmpleadoDTO {
    private Long empleadoId;
    private String nombreEmpleado;
    private Double totalHorasExtra = 0.0;      // Total mensual de horas 50%
    private Double totalFindeFeriado = 0.0;     // Total mensual de horas 100%
    private Integer totalGuardias = 0;
    private String tipoContrato;              // ← NUEVO (para el label)
    private Empleado.TipoContrato tipoContratoEnum;
}
