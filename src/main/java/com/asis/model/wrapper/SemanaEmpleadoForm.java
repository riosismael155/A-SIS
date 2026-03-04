package com.asis.model.wrapper;

import com.asis.model.dto.SemanaEmpleadoDTO;
import lombok.Data;

import java.util.List;
@Data
public class SemanaEmpleadoForm {

    private Long controlId;

    private List<SemanaEmpleadoDTO> items;



}