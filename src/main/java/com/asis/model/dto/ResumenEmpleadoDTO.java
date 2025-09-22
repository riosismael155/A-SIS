package com.asis.model.dto;

import com.asis.model.Ausencia;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResumenEmpleadoDTO {
    // --- Para resumen general ---
    private String dni;
    private String nombreCompleto;
    private double horasNormales;
    private double horasExtras;
    private double horasFinDeSemana;
    private long llegadasTarde;

    // --- Para detalle diario ---
    private String fechaFormateada;
    private String nombreDia;
    private LocalTime horaEntrada;
    private LocalTime horaSalida;
    private String tipoHora;
    private double horasTrabajadas;
    private boolean ausente;

    // --- Totales que se cargan en el primer DTO del período ---
    private double totalHoras;
    private double totalNormales;
    private double totalExtras;
    private double totalFinde;
    private int totalAusencias;
    private Ausencia.TipoDeAusencia tipoDeAusencia;
    private int totalLlegadasTarde;
    private int totalIncompletos;

    // --- Flags útiles para la UI ---
    private boolean llegoTarde;
    private boolean esFeriado;
    private boolean esFinDeSemana;
    private boolean justificada;

    // --- NUEVO: diferenciación de origen de horas ---
    private boolean tieneHorasJustificadas;
    private boolean tieneHorasReales;
    private double horasJustificadasDia;
    private double horasRealesDia;

    private int diasTrabajados;
    private int minutosTardeTotales;

    private boolean marcaIncompleta;
    private String tipoIncompleto;
    private double despuesDeHora; // minutos trabajados después del horario
    // En ResumenEmpleadoDTO agregar:
    private boolean segundoHorario;
    private int minutosTarde;
    private String fotoEntradaBase64;
    private String fotoSalidaBase64;
    private boolean presentismo;



// Agregar getter y setter (si usas Lombok @Data, se generan automáticamente)


    // Constructor para resumen general
    public ResumenEmpleadoDTO(String dni, String nombreCompleto,
                              double horasNormales, double horasExtras,
                              double horasFinDeSemana, long llegadasTarde) {
        this.dni = dni;
        this.nombreCompleto = nombreCompleto;
        this.horasNormales = horasNormales;
        this.horasExtras = horasExtras;
        this.horasFinDeSemana = horasFinDeSemana;
        this.llegadasTarde = llegadasTarde;
    }

    // Constructor completo para detalle diario
    public ResumenEmpleadoDTO(String dni, String nombreCompleto,
                              double totalHoras, double totalNormales, double totalExtras, double totalFinde,
                              String fechaFormateada, String nombreDia,
                              LocalTime horaEntrada, LocalTime horaSalida,
                              String tipoHora, double horasTrabajadas,
                              boolean ausente,
                              int totalAusencias, int totalLlegadasTarde, int totalIncompletos,
                              boolean llegoTarde,
                              boolean esFeriado, boolean esFinDeSemana,
                              boolean justificada,
                              boolean tieneHorasJustificadas, boolean tieneHorasReales,
                              double horasJustificadasDia, double horasRealesDia,
                              int diasTrabajados, int minutosTardeTotales, double despuesDeHora, int minutosTarde, String fotoEntradaBase64, String fotoSalidaBase64,boolean presentismo) {
        this.dni = dni;
        this.nombreCompleto = nombreCompleto;
        this.totalHoras = totalHoras;
        this.totalNormales = totalNormales;
        this.totalExtras = totalExtras;
        this.totalFinde = totalFinde;
        this.fechaFormateada = fechaFormateada;
        this.nombreDia = nombreDia;
        this.horaEntrada = horaEntrada;
        this.horaSalida = horaSalida;
        this.tipoHora = tipoHora;
        this.horasTrabajadas = horasTrabajadas;
        this.ausente = ausente;
        this.totalAusencias = totalAusencias;
        this.totalLlegadasTarde = totalLlegadasTarde;
        this.totalIncompletos = totalIncompletos;
        this.llegoTarde = llegoTarde;
        this.esFeriado = esFeriado;
        this.esFinDeSemana = esFinDeSemana;
        this.justificada = justificada;
        this.tieneHorasJustificadas = tieneHorasJustificadas;
        this.tieneHorasReales = tieneHorasReales;
        this.horasJustificadasDia = horasJustificadasDia;
        this.horasRealesDia = horasRealesDia;
        this.diasTrabajados = diasTrabajados;
        this.minutosTardeTotales = minutosTardeTotales;
        this.despuesDeHora = despuesDeHora;
        this.minutosTarde = minutosTarde;
        this.fotoEntradaBase64 = fotoEntradaBase64;
        this.fotoSalidaBase64 = fotoSalidaBase64;
        this.presentismo = presentismo;
    }
}