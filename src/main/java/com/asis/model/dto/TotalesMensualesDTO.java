package com.asis.model.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;


@Data
public class TotalesMensualesDTO {
    private Map<Long, TotalesEmpleadoDTO> totalesPorEmpleado;
    private Map<String, List<TotalesEmpleadoDTO>> empleadosPorTipoContrato;
    private Map<String, TotalesEmpleadoDTO> subtotalesPorTipo;  // ← NUEVO
    private TotalesEmpleadoDTO totalesGenerales;
}