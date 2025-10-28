package com.asis.model.dto;

import com.asis.model.Ausencia;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AusenciaDTO {
    private Long id;
    private String nombreEmpleado;
    private String apellidoEmpleado;
    private LocalDate desde;
    private LocalDate hasta;
    private String descripcion;
    private String tipoDeAusencia;

    public static AusenciaDTO fromEntity(Ausencia a) {
        return AusenciaDTO.builder()
                .id(a.getId())
                .nombreEmpleado(a.getEmpleado().getNombre())
                .apellidoEmpleado(a.getEmpleado().getApellido())
                .desde(a.getDesde())
                .hasta(a.getHasta())
                .descripcion(a.getDescripcion())
                .tipoDeAusencia(a.getTipoDeAusencia().name())
                .build();
    }
}



