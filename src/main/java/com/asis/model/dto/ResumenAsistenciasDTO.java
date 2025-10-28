package com.asis.model.dto;

import com.asis.model.Empleado;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResumenAsistenciasDTO {
    private String dni;
    private String nombre;
    private String apellido;
    private double totalHoras;
    private double totalNormales;
    private double totalExtras;
    private double totalFinde;
    private long totalAusencias;
    private long totalLlegadasTarde;
    private Empleado.TipoContrato tipoContrato;
    boolean presentismo;
    private boolean tieneMarcasIncompletas;
    private long totalFaltasConAviso;
    private long totalVacaciones;
    private long totalAusenciasJustificadas;


}




