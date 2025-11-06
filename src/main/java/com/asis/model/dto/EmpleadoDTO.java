package com.asis.model.dto;

import com.asis.model.Empleado;
import lombok.Data;

@Data
public class EmpleadoDTO {

    private Long id;
    private String nombre;
    private String apellido;
    private String dni;

    private String horaEntrada;
    private String horaSalida;

    private String horaEntrada2;
    private String horaSalida2;

    private String horaEntrada3;
    private String horaSalida3;

    private String horaEntrada4;
    private String horaSalida4;

    private Integer flexMinutos;

    private Long areaId;
    private String areaNombre;

    private String tipoContrato;

    private Long usuarioId;

    public EmpleadoDTO(Empleado e) {
        this.id = e.getId();
        this.nombre = e.getNombre();
        this.apellido = e.getApellido();
        this.dni = e.getDni();

        this.horaEntrada = format(e.getHoraEntrada());
        this.horaSalida = format(e.getHoraSalida());

        this.horaEntrada2 = format(e.getHoraEntrada2());
        this.horaSalida2 = format(e.getHoraSalida2());

        this.horaEntrada3 = format(e.getHoraEntrada3());
        this.horaSalida3 = format(e.getHoraSalida3());

        this.horaEntrada4 = format(e.getHoraEntrada4());
        this.horaSalida4 = format(e.getHoraSalida4());

        this.flexMinutos = e.getFlexMinutos();

        if (e.getArea() != null) {
            this.areaId = e.getArea().getId();
            this.areaNombre = e.getArea().getNombre();
        }

        this.tipoContrato = (e.getTipoContrato() != null) ? e.getTipoContrato().name() : null;

        this.usuarioId = (e.getUsuario() != null) ? e.getUsuario().getId() : null;
    }

    private String format(java.time.LocalTime t) {
        return (t != null) ? t.toString() : null;
    }
}
