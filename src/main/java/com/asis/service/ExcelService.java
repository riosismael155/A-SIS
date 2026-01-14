package com.asis.service;

import com.asis.model.*;
import com.asis.model.dto.*;
import com.asis.repository.EmpleadoRepository;
import com.asis.repository.LogCargaAsistenciaRepository;
import com.asis.repository.RegistroRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelService {

    private final EmpleadoRepository empleadoRepo;
    private final RegistroRepository registroRepository;
    private final JustificacionAusenciaService ausenciaService;
    private final LogCargaAsistenciaRepository logCargaRepository;
    private final FeriadoService feriadoService;

    //LOGICA PARA TRATAR EL ARCHIVO DE EXCEL
    @Transactional
    public void cargarYGuardarAsistencias(CargaAsistenciaDTO dto) {
        // 1. Extraer marcas válidas
        List<Marca> marcasValidas = extraerMarcasValidas(dto.getArchivo(), dto.getDesde(), dto.getHasta());

        // 2. Convertir a registros de asistencia
        List<RegistroAsistencia> registros = convertirMarcasARegistros(marcasValidas);

        // 3. Guardar registros
        registroRepository.saveAll(registros);

        // 4. Generar descripción automática
        String descripcion = generarDescripcion(dto.getDesde(), dto.getHasta());
        dto.setDescripcion(descripcion);

        // 5. Registrar log de carga
        LogCargaAsistencia log = new LogCargaAsistencia();
        log.setDesde(dto.getDesde());
        log.setHasta(dto.getHasta());
        log.setDescripcion(descripcion);
        log.setFechaCarga(LocalDateTime.now().minusHours(3));

        log.setCantidadRegistros(registros.size());

        logCargaRepository.save(log);
    }

    public String generarDescripcion(LocalDate desde, LocalDate hasta) {
        DateTimeFormatter formatoDiaMes = DateTimeFormatter.ofPattern("d 'de' MMMM", new Locale("es", "ES"));
        DateTimeFormatter formatoMes = DateTimeFormatter.ofPattern("MMMM", new Locale("es", "ES"));

        // Detectar si cubre todo el período laboral (21 al 20)
        boolean periodoCompleto = desde.getDayOfMonth() == 21 && hasta.getDayOfMonth() == 20 &&
                desde.plusMonths(1).getMonth() == hasta.getMonth();

        // Determinar mes laboral al que pertenece el rango
        LocalDate mesLaboralReferencia = hasta.getDayOfMonth() >= 21
                ? hasta.plusMonths(1)
                : hasta;

        String mesLaboral = capitalize(mesLaboralReferencia.format(formatoMes));
        int anioLaboral = mesLaboralReferencia.getYear();

        // Descripción adaptativa
        if (periodoCompleto) {
            return "Mes laboral " + mesLaboral + " " + anioLaboral;
        } else {
            String rango = "Semana del " + desde.format(formatoDiaMes) + " al " + hasta.format(formatoDiaMes);
            return rango;
        }
    }

    private String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }

    public List<Marca> extraerMarcasValidas(MultipartFile archivo, LocalDate desde, LocalDate hasta) {
        List<Marca> todas = leerExcel(archivo);
        return todas.stream()
                .filter(m -> !m.getFecha().isBefore(desde) && !m.getFecha().isAfter(hasta))
                .filter(m -> empleadoRepo.findByDni(m.getDni()).isPresent())
                .toList();
    }

    private List<Marca> leerExcel(MultipartFile file) {
        List<Marca> marcas = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; // Saltar encabezado

                // Leer columna 3 (índice 2) como DNI (EnNo)
                Cell cellDni = row.getCell(2);
                String dni = obtenerDniComoTexto(cellDni);

                // Leer columna 10 (índice 9) como fecha y hora
                Cell cellFechaHora = row.getCell(9);
                if (cellFechaHora == null || cellFechaHora.getCellType() != CellType.NUMERIC || !DateUtil.isCellDateFormatted(cellFechaHora)) {
                    continue; // saltar si no es fecha válida
                }

                LocalDateTime fechaHora = cellFechaHora.getLocalDateTimeCellValue();
                marcas.add(new Marca(dni, fechaHora.toLocalDate(), fechaHora.toLocalTime()));
            }
        } catch (Exception e) {
            throw new RuntimeException("Error al leer el archivo Excel", e);
        }

        return marcas;
    }

    private String obtenerDniComoTexto(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC) {
            return String.valueOf((long) cell.getNumericCellValue());
        } else {
            return cell.getStringCellValue().trim();
        }
    }

    public List<RegistroAsistencia> convertirMarcasARegistros(List<Marca> marcas) {
        Map<String, List<Marca>> marcasPorDni = marcas.stream()
                .sorted(Comparator.comparing(Marca::getFecha).thenComparing(Marca::getHora))
                .collect(Collectors.groupingBy(Marca::getDni));

        List<RegistroAsistencia> registros = new ArrayList<>();

        for (var entry : marcasPorDni.entrySet()) {
            String dni = entry.getKey();
            Empleado emp = empleadoRepo.findByDni(dni).orElseThrow();
            Map<LocalDate, List<Marca>> porDia = entry.getValue().stream()
                    .collect(Collectors.groupingBy(Marca::getFecha));

            for (var dia : porDia.entrySet()) {
                List<Marca> delDia = dia.getValue();
                for (int i = 0; i < delDia.size(); i++) {
                    Marca marca = delDia.get(i);
                    RegistroAsistencia reg = new RegistroAsistencia();
                    reg.setEmpleado(emp);
                    reg.setFecha(marca.getFecha());
                    reg.setHora(marca.getHora());
                    reg.setOrdenDia(i + 1);
                    reg.setTipoHora(detectarTipoHora(emp, marca.getFecha(), i));
                    registros.add(reg);
                }
            }
        }
        return registros;
    }
    //LOGICA PARA TRATAR EL ARCHIVO DE EXCEL

    //PROCESAMIENTO DE LOS DATOS ETRAIDOS DE LA PLANILLA
    public List<ResumenEmpleadoDTO> procesarAsistenciasTurnosAnormales(
            Empleado empleado,
            List<RegistroAsistencia> registros,
            LocalDate desde,
            LocalDate hasta) {

        // 1. ORDENAR TODOS LOS REGISTROS DE FORMA CONTINUA
        List<RegistroAsistencia> registrosOrdenados = registros.stream()
                .sorted(Comparator.comparing(RegistroAsistencia::getFecha)
                        .thenComparing(RegistroAsistencia::getHora))
                .toList();

        List<ResumenEmpleadoDTO> detalle = new ArrayList<>();
        DateTimeFormatter formatoLatino = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Locale localeEs = new Locale("es", "ES");

        // 2. MAPA PARA CONTROLAR REGISTROS PROCESADOS
        Set<Long> registrosProcesados = new HashSet<>();

        // 3. MAPAS PARA ALMACENAR DATOS POR DÍA
        Map<LocalDate, List<Double>> horasPorDia = new HashMap<>();
        Map<LocalDate, List<ResumenEmpleadoDTO>> dtosPorDia = new HashMap<>();
        Map<LocalDate, Integer> contadorParesPorDia = new HashMap<>();

        // 4. CALCULAR HORAS DE TURNOS PARA LA LÓGICA DEL UMBRAL
        double horasTurno1 = 0;
        double horasTurno2 = 0;
        boolean tieneDosTurnos = false;
        boolean turnosIgualHoras = false;

        if (empleado.getHoraEntrada() != null && empleado.getHoraSalida() != null) {
            horasTurno1 = Duration.between(empleado.getHoraEntrada(), empleado.getHoraSalida()).toMinutes() / 60.0;

            if (empleado.getHoraEntrada2() != null && empleado.getHoraSalida2() != null) {
                horasTurno2 = Duration.between(empleado.getHoraEntrada2(), empleado.getHoraSalida2()).toMinutes() / 60.0;
                tieneDosTurnos = true;
                turnosIgualHoras = Math.abs(horasTurno1 - horasTurno2) < 0.01;
            }
        }

        // 5. PROCESAR REGISTROS DE FORMA CONTINUA
        for (int i = 0; i < registrosOrdenados.size(); i++) {
            RegistroAsistencia entrada = registrosOrdenados.get(i);

            // Si ya procesamos este registro, saltar
            if (registrosProcesados.contains(entrada.getId())) {
                continue;
            }

            // Buscar la salida correspondiente
            RegistroAsistencia salida = null;
            for (int j = i + 1; j < registrosOrdenados.size(); j++) {
                RegistroAsistencia posibleSalida = registrosOrdenados.get(j);

                // Si encontramos una salida que no ha sido procesada
                if (!registrosProcesados.contains(posibleSalida.getId())) {
                    salida = posibleSalida;
                    break;
                }
            }

            // Si no encontramos salida, es registro incompleto
            if (salida == null) {
                // AGREGAR REGISTRO INCOMPLETO
                String nombreDia = entrada.getFecha().getDayOfWeek().getDisplayName(TextStyle.FULL, localeEs);
                String fechaFormateada = entrada.getFecha().format(formatoLatino);
                boolean faltaSalida = entrada.getOrdenDia() % 2 == 1;

                String fotoBase64 = null;
                if (entrada.getFoto() != null && entrada.getFoto().length > 0) {
                    fotoBase64 = Base64.getEncoder().encodeToString(entrada.getFoto()).replaceAll("\\s+", "");
                }

                ResumenEmpleadoDTO dtoIncompleto = new ResumenEmpleadoDTO();
                dtoIncompleto.setDni(empleado.getDni());
                dtoIncompleto.setNombreCompleto(empleado.getNombre() + " " + empleado.getApellido());
                dtoIncompleto.setFechaFormateada(fechaFormateada);
                dtoIncompleto.setNombreDia(nombreDia);
                dtoIncompleto.setMarcaIncompleta(true);
                dtoIncompleto.setTipoIncompleto(faltaSalida ? "FALTA_SALIDA" : "FALTA_ENTRADA");
                dtoIncompleto.setHoraEntrada(faltaSalida ? entrada.getHora() : null);
                dtoIncompleto.setHoraSalida(faltaSalida ? null : entrada.getHora());
                dtoIncompleto.setTipoHora("INCOMPLETO");
                dtoIncompleto.setHorasTrabajadas(0);
                dtoIncompleto.setAusente(false);
                dtoIncompleto.setEsFeriado(feriadoService.esFeriado(entrada.getFecha()));
                dtoIncompleto.setEsFinDeSemana(entrada.getFecha().getDayOfWeek() == DayOfWeek.SATURDAY || entrada.getFecha().getDayOfWeek() == DayOfWeek.SUNDAY);
                dtoIncompleto.setJustificada(false);
                dtoIncompleto.setHorasExtras(0);
                dtoIncompleto.setHorasNormales(0);

                if (faltaSalida) {
                    dtoIncompleto.setFotoEntradaBase64(fotoBase64);
                    dtoIncompleto.setFotoSalidaBase64(null);
                } else {
                    dtoIncompleto.setFotoEntradaBase64(null);
                    dtoIncompleto.setFotoSalidaBase64(fotoBase64);
                }

                detalle.add(dtoIncompleto);
                registrosProcesados.add(entrada.getId());
                continue;
            }

            // CALCULAR HORAS TRABAJADAS
            double horas;
            LocalDateTime fechaHoraEntrada = LocalDateTime.of(entrada.getFecha(), entrada.getHora());
            LocalDateTime fechaHoraSalida = LocalDateTime.of(salida.getFecha(), salida.getHora());

            // CORRECIÓN: Si la salida es anterior a la entrada, asumimos cruce de medianoche
            boolean esMismoDia = entrada.getFecha().equals(salida.getFecha());
            boolean salidaAnteriorAEntrada = salida.getHora().isBefore(entrada.getHora());

            if (!esMismoDia || (esMismoDia && salidaAnteriorAEntrada)) {
                if (esMismoDia && salidaAnteriorAEntrada) {
                    fechaHoraSalida = fechaHoraSalida.plusDays(1);
                }
            }

            Duration duracion = Duration.between(fechaHoraEntrada, fechaHoraSalida);
            horas = duracion.toMinutes() / 60.0;

            // Validar que las horas no sean negativas
            if (horas < 0) {
                fechaHoraSalida = LocalDateTime.of(salida.getFecha().plusDays(1), salida.getHora());
                duracion = Duration.between(fechaHoraEntrada, fechaHoraSalida);
                horas = duracion.toMinutes() / 60.0;
            }

            // Asegurar que horas no sea negativo
            horas = Math.max(0, horas);

            // VERIFICAR SI ES TURNO NOCTURNO
            boolean esTurnoNocturno = false;
            LocalDate fechaSalida = salida.getFecha();
            LocalDate fechaEntrada = entrada.getFecha();

            // Caso 1: Entrada en un día y salida al día siguiente
            if (fechaSalida.isAfter(fechaEntrada)) {
                esTurnoNocturno = true;
            }
            // Caso 2: Mismo día con salida anterior a entrada (error de datos que indica cruce)
            else if (salida.getHora().isBefore(entrada.getHora())) {
                esTurnoNocturno = true;
            }

            // PREPARAR DATOS BÁSICOS
            String nombreDia = entrada.getFecha().getDayOfWeek().getDisplayName(TextStyle.FULL, localeEs);
            String fechaFormateada = entrada.getFecha().format(formatoLatino);
            LocalDate fechaDia = entrada.getFecha();

            String fotoEntradaBase64 = (entrada.getFoto() != null) ?
                    Base64.getEncoder().encodeToString(entrada.getFoto()).replaceAll("\\s+", "") : null;
            String fotoSalidaBase64 = (salida.getFoto() != null) ?
                    Base64.getEncoder().encodeToString(salida.getFoto()).replaceAll("\\s+", "") : null;

            // CONTAR PARES POR DÍA
            int numeroPar = contadorParesPorDia.getOrDefault(fechaDia, 0) + 1;
            contadorParesPorDia.put(fechaDia, numeroPar);

            // ALMACENAR DATOS PARA PROCESAMIENTO POSTERIOR
            ResumenEmpleadoDTO dto = new ResumenEmpleadoDTO();
            dto.setDni(empleado.getDni());
            dto.setNombreCompleto(empleado.getNombre() + " " + empleado.getApellido());
            dto.setFechaFormateada(fechaFormateada);
            dto.setNombreDia(nombreDia);
            dto.setHoraEntrada(entrada.getHora());
            dto.setHoraSalida(salida.getHora());
            dto.setHorasTrabajadas(horas);
            dto.setMarcaIncompleta(false);
            dto.setAusente(false);
            dto.setLlegoTarde(false);
            dto.setMinutosTarde(0);
            dto.setEsFeriado(feriadoService.esFeriado(fechaDia));
            dto.setEsFinDeSemana(fechaDia.getDayOfWeek() == DayOfWeek.SATURDAY || fechaDia.getDayOfWeek() == DayOfWeek.SUNDAY);
            dto.setJustificada(entrada.getJustificacion() != null);
            dto.setFotoEntradaBase64(fotoEntradaBase64);
            dto.setFotoSalidaBase64(fotoSalidaBase64);

            if (esTurnoNocturno) {
                dto.setMuestraCruceMedianoche(true);
                dto.setHoraSalidaReal(salida.getHora());
            }

            // VERIFICAR AUSENCIA
            Ausencia ausenciaDia = null;
            boolean esLaboral = !dto.isEsFeriado() && !dto.isEsFinDeSemana();
            if (esLaboral) {
                ausenciaDia = ausenciaService.obtenerAusenciaEmpleadoEnFecha(empleado, fechaDia);
                if (ausenciaDia != null) {
                    dto.setTipoDeAusencia(ausenciaDia.getTipoDeAusencia());
                    dto.setJustificada(ausenciaDia.getTipoDeAusencia() != Ausencia.TipoDeAusencia.FALTA_SIN_AVISO);
                }
            }

            // ALMACENAR PARA PROCESAMIENTO POSTERIOR
            if (!horasPorDia.containsKey(fechaDia)) {
                horasPorDia.put(fechaDia, new ArrayList<>());
                dtosPorDia.put(fechaDia, new ArrayList<>());
            }
            horasPorDia.get(fechaDia).add(horas);
            dtosPorDia.get(fechaDia).add(dto);

            registrosProcesados.add(entrada.getId());
            registrosProcesados.add(salida.getId());
        }

        // 6. APLICAR LÓGICA DEL UMBRAL PARA HORAS EXTRAS POR DÍA
        for (LocalDate fecha : horasPorDia.keySet()) {
            List<Double> horasDelDia = horasPorDia.get(fecha);
            List<ResumenEmpleadoDTO> dtosDelDia = dtosPorDia.get(fecha);

            boolean esFeriado = feriadoService.esFeriado(fecha);
            boolean esFinDeSemana = fecha.getDayOfWeek() == DayOfWeek.SATURDAY || fecha.getDayOfWeek() == DayOfWeek.SUNDAY;
            boolean esLaboral = !esFeriado && !esFinDeSemana;

            // VERIFICAR AUSENCIAS PARA EL DÍA
            Ausencia ausenciaDia = null;
            boolean tieneNoMarco = false;
            boolean tieneAusenciaJustificada = false;
            boolean tieneFaltaConAviso = false;

            if (esLaboral) {
                ausenciaDia = ausenciaService.obtenerAusenciaEmpleadoEnFecha(empleado, fecha);
                if (ausenciaDia != null) {
                    tieneNoMarco = (ausenciaDia.getTipoDeAusencia() == Ausencia.TipoDeAusencia.NO_MARCO);
                    tieneAusenciaJustificada = (ausenciaDia.getTipoDeAusencia() == Ausencia.TipoDeAusencia.JUSTIFICADA
                            || ausenciaDia.getTipoDeAusencia() == Ausencia.TipoDeAusencia.VACACIONES);
                    tieneFaltaConAviso = (ausenciaDia.getTipoDeAusencia() == Ausencia.TipoDeAusencia.FALTA_CON_AVISO);
                }
            }

            double horasTotalesDelDia = horasDelDia.stream().mapToDouble(Double::doubleValue).sum();
            double horasRealesTotales = 0;

            // Calcular horas reales (no justificadas)
            for (int i = 0; i < dtosDelDia.size(); i++) {
                if (!dtosDelDia.get(i).isJustificada()) {
                    horasRealesTotales += horasDelDia.get(i);
                }
            }

            // NUEVA LÓGICA: DETERMINAR UMBRAL PARA HORAS EXTRAS
            boolean aplicarHorasExtras = false;
            double horasConsideradasParaCalculo = 0;
            double umbralHorasExtras = 0;

            if (esLaboral) {
                // Determinar qué horas considerar según el tipo de día
                if (tieneNoMarco) {
                    // Para NO_MARCO, considerar TODAS las horas trabajadas
                    horasConsideradasParaCalculo = horasTotalesDelDia;
                } else if (tieneAusenciaJustificada || tieneFaltaConAviso) {
                    // Para días JUSTIFICADOS o FALTA_CON_AVISO, no aplicar horas extras
                    aplicarHorasExtras = false;
                    horasConsideradasParaCalculo = horasRealesTotales;
                } else {
                    // Días normales sin ausencia
                    horasConsideradasParaCalculo = horasRealesTotales;
                }

                // LÓGICA NUEVA: Determinar umbral según el patrón de trabajo del día
                if ((!tieneAusenciaJustificada && !tieneFaltaConAviso) || tieneNoMarco) {
                    // Analizar patrón de trabajo del día
                    boolean trabajoEnPrimerTurno = false;
                    boolean trabajoEnSegundoTurno = false;

                    // Para empleados con turnos anormales, analizamos el tiempo trabajado
                    // Si trabajó más de 6 horas, asumimos que trabajó en "ambos turnos"
                    double horasEnPrimerTurno = horasConsideradasParaCalculo >= 6 ? 6 : horasConsideradasParaCalculo;
                    double horasEnSegundoTurno = Math.max(0, horasConsideradasParaCalculo - 6);

                    trabajoEnPrimerTurno = horasEnPrimerTurno > 0;
                    trabajoEnSegundoTurno = horasEnSegundoTurno > 0;

                    // CASO 1: Si los dos turnos tienen la misma cantidad de horas
                    if (tieneDosTurnos && turnosIgualHoras) {
                        if (trabajoEnPrimerTurno && !trabajoEnSegundoTurno) {
                            // Solo trabajó en el primer turno - usar solo ese turno
                            umbralHorasExtras = horasTurno1 + 0.5;
                        } else if (!trabajoEnPrimerTurno && trabajoEnSegundoTurno) {
                            // Solo trabajó en el segundo turno - usar solo ese turno
                            umbralHorasExtras = horasTurno2 + 0.5;
                        } else if (trabajoEnPrimerTurno && trabajoEnSegundoTurno) {
                            // Trabajó en ambos turnos - usar solo el primero (ya que son iguales)
                            umbralHorasExtras = horasTurno1 + 0.5;
                        }
                    }
                    // CASO 2: Los turnos tienen distinta cantidad de horas
                    else if (tieneDosTurnos) {
                        // Siempre sumar ambos turnos para el umbral
                        umbralHorasExtras = (horasTurno1 + horasTurno2) + 0.5;
                    }
                    // CASO 3: Sin horario definido (empleados con turnos anormales)
                    else {
                        // Para empleados sin horario normal, usar umbral de 6 horas + 0.5
                        umbralHorasExtras = 6 + 0.5;
                    }

                    // Aplicar horas extras si se supera el umbral
                    aplicarHorasExtras = horasConsideradasParaCalculo > umbralHorasExtras;
                }
            }

            // DISTRIBUIR HORAS NORMALES Y EXTRAS
            if (esFeriado || esFinDeSemana) {
                // Días feriados o fin de semana - todas las horas son extras
                for (int i = 0; i < dtosDelDia.size(); i++) {
                    ResumenEmpleadoDTO dto = dtosDelDia.get(i);
                    double horas = horasDelDia.get(i);

                    String tipoHora = esFeriado ? "FERIADO" : "FIN_SEMANA";
                    dto.setTipoHora(tipoHora);
                    dto.setHorasNormales(0.0);
                    dto.setHorasExtras(horas);

                    if (ausenciaDia != null) {
                        dto.setTipoDeAusencia(ausenciaDia.getTipoDeAusencia());
                    }
                }
            } else if (aplicarHorasExtras && !dtosDelDia.isEmpty()) {
                // Calcular horas extras totales del día
                double horasExtrasTotales = Math.max(0, horasConsideradasParaCalculo - umbralHorasExtras + 0.5);
                double horasNormalesDelDia = horasConsideradasParaCalculo - horasExtrasTotales;

                // Identificar índices con horas a distribuir
                List<Integer> indicesParaDistribuir = new ArrayList<>();
                for (int i = 0; i < dtosDelDia.size(); i++) {
                    if (tieneNoMarco || !dtosDelDia.get(i).isJustificada()) {
                        indicesParaDistribuir.add(i);
                    }
                }

                // Distribuir horas NORMALMENTE hasta llenar el turno
                double horasNormalesAsignadas = 0;

                for (int i = 0; i < indicesParaDistribuir.size(); i++) {
                    int idx = indicesParaDistribuir.get(i);
                    ResumenEmpleadoDTO dto = dtosDelDia.get(idx);
                    double horasPar = horasDelDia.get(idx);

                    if (horasNormalesAsignadas < horasNormalesDelDia) {
                        // Aún hay horas normales por asignar
                        double horasNormalesParaEstePar = Math.min(horasPar, horasNormalesDelDia - horasNormalesAsignadas);
                        double horasExtrasParaEstePar = horasPar - horasNormalesParaEstePar;

                        String tipoHora;
                        if (horasExtrasParaEstePar > 0 && horasNormalesParaEstePar > 0) {
                            tipoHora = "MIXTO";
                        } else if (horasExtrasParaEstePar > 0) {
                            tipoHora = "EXTRA";
                        } else {
                            tipoHora = "NORMAL";
                        }

                        dto.setTipoHora(tipoHora);
                        dto.setHorasNormales(horasNormalesParaEstePar);
                        dto.setHorasExtras(horasExtrasParaEstePar);

                        horasNormalesAsignadas += horasNormalesParaEstePar;
                    } else {
                        // Ya se asignaron todas las horas normales, el resto es extra puro
                        dto.setTipoHora("EXTRA");
                        dto.setHorasNormales(0.0);
                        dto.setHorasExtras(horasPar);
                    }
                }

                // Para los registros justificados (si los hay)
                for (int i = 0; i < dtosDelDia.size(); i++) {
                    ResumenEmpleadoDTO dto = dtosDelDia.get(i);
                    if (dto.isJustificada() && !indicesParaDistribuir.contains(i)) {
                        dto.setTipoHora("JUSTIFICADA");
                        dto.setHorasNormales(0.0);
                        dto.setHorasExtras(0.0);
                    }
                }
            } else if (!dtosDelDia.isEmpty()) {
                // Días sin horas extras - CORRECCIÓN AQUÍ
                for (int i = 0; i < dtosDelDia.size(); i++) {
                    ResumenEmpleadoDTO dto = dtosDelDia.get(i);
                    double horas = horasDelDia.get(i);

                    // CASO ESPECIAL: NO_MARCO - siempre debe ser NORMAL
                    if (tieneNoMarco) {
                        dto.setTipoHora("NORMAL");
                        dto.setHorasNormales(horas);
                        dto.setHorasExtras(0.0);
                    }
                    // Para ausencias justificadas o falta con aviso
                    else if (tieneAusenciaJustificada || tieneFaltaConAviso) {
                        dto.setTipoHora("JUSTIFICADA");
                        dto.setHorasNormales(0.0);
                        dto.setHorasExtras(0.0);
                    }
                    // Para días normales sin ausencia o con otras ausencias
                    else {
                        // Solo asignar como normales las horas no justificadas
                        if (!dto.isJustificada()) {
                            dto.setTipoHora("NORMAL");
                            dto.setHorasNormales(horas);
                            dto.setHorasExtras(0.0);
                        } else {
                            dto.setTipoHora("JUSTIFICADA");
                            dto.setHorasNormales(0.0);
                            dto.setHorasExtras(0.0);
                        }
                    }
                }
            }


            // Agregar DTOs procesados a la lista final
            detalle.addAll(dtosDelDia);
        }

        // 7. AGREGAR DÍAS SIN REGISTROS
        Map<LocalDate, List<RegistroAsistencia>> registrosPorDia = registros.stream()
                .collect(Collectors.groupingBy(RegistroAsistencia::getFecha));

        LocalDate actual = desde;
        while (!actual.isAfter(hasta)) {
            if (!registrosPorDia.containsKey(actual)) {
                String nombreDia = actual.getDayOfWeek().getDisplayName(TextStyle.FULL, localeEs);
                String fechaFormateada = actual.format(formatoLatino);

                boolean esFeriado = feriadoService.esFeriado(actual);
                boolean esFinDeSemana = actual.getDayOfWeek() == DayOfWeek.SATURDAY || actual.getDayOfWeek() == DayOfWeek.SUNDAY;
                boolean esLaboral = !esFeriado && !esFinDeSemana;

                ResumenEmpleadoDTO dto = new ResumenEmpleadoDTO();
                dto.setDni(empleado.getDni());
                dto.setNombreCompleto(empleado.getNombre() + " " + empleado.getApellido());
                dto.setFechaFormateada(fechaFormateada);
                dto.setNombreDia(nombreDia);
                dto.setHorasTrabajadas(0);
                dto.setEsFeriado(esFeriado);
                dto.setEsFinDeSemana(esFinDeSemana);
                dto.setHorasExtras(0);
                dto.setHorasNormales(0);

                if (esLaboral) {
                    Ausencia ausencia = ausenciaService.obtenerAusenciaEmpleadoEnFecha(empleado, actual);
                    if (ausencia != null && ausencia.getTipoDeAusencia() != Ausencia.TipoDeAusencia.FALTA_SIN_AVISO) {
                        dto.setTipoHora("NORMAL");
                        dto.setAusente(false);
                        dto.setJustificada(true);
                        dto.setTipoDeAusencia(ausencia.getTipoDeAusencia());

                        // Calcular horas para ausencias justificadas
                        if (ausencia.getTipoDeAusencia() == Ausencia.TipoDeAusencia.NO_MARCO) {
                            double horasNormalesParaNoMarco = 0;
                            if (tieneDosTurnos && turnosIgualHoras) {
                                horasNormalesParaNoMarco = horasTurno1;
                            } else if (tieneDosTurnos) {
                                horasNormalesParaNoMarco = horasTurno1 + horasTurno2;
                            } else {
                                horasNormalesParaNoMarco = 8.0; // Valor por defecto
                            }
                            dto.setHorasTrabajadas(horasNormalesParaNoMarco);
                            dto.setHorasNormales(horasNormalesParaNoMarco);
                        } else {
                            dto.setHorasTrabajadas(0);
                        }
                    } else {
                        dto.setTipoHora("AUSENTE");
                        dto.setAusente(true);
                        dto.setJustificada(false);
                        dto.setTipoDeAusencia(Ausencia.TipoDeAusencia.FALTA_SIN_AVISO);
                    }
                } else {
                    dto.setTipoHora(esFeriado ? "FERIADO" : "FIN_SEMANA");
                    dto.setAusente(false);
                    dto.setJustificada(false);
                }

                detalle.add(dto);
            }
            actual = actual.plusDays(1);
        }

        // 8. CALCULAR PRESENTISMO (sin considerar tardanzas)
        boolean tieneAusenciaQueQuitaPresentismo = detalle.stream().anyMatch(dto -> {
            Ausencia.TipoDeAusencia tipo = dto.getTipoDeAusencia();
            return tipo != null
                    && tipo != Ausencia.TipoDeAusencia.VACACIONES
                    && tipo != Ausencia.TipoDeAusencia.NO_MARCO;
        });

        // Presentismo solo considera ausencias, no tardanzas
        boolean presentismo = !tieneAusenciaQueQuitaPresentismo;

        detalle.forEach(dto -> dto.setPresentismo(presentismo));
        // 9. ORDENAR LOS RESULTADOS POR FECHA
        detalle.sort(Comparator.comparing(dto -> {
            try {
                // Convertir la fecha formateada (dd/MM/yyyy) a LocalDate para ordenar
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                return LocalDate.parse(dto.getFechaFormateada(), formatter);
            } catch (Exception e) {
                // Si hay error, retornar fecha mínima
                return LocalDate.MIN;
            }
        }));

        return detalle;
    }


    public List<ResumenEmpleadoDTO> procesarAsistenciasEmpleado(
            Empleado empleado,
            List<RegistroAsistencia> registros,
            LocalDate desde,
            LocalDate hasta) {

        Map<LocalDate, List<RegistroAsistencia>> registrosPorDia = registros.stream()
                .sorted(Comparator.comparing(RegistroAsistencia::getHora))
                .collect(Collectors.groupingBy(RegistroAsistencia::getFecha));

        List<ResumenEmpleadoDTO> detalle = new ArrayList<>();
        DateTimeFormatter formatoLatino = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Locale localeEs = new Locale("es", "ES");

        double totalHoras = 0;
        double totalNormales = 0;
        double totalExtras = 0;
        double totalFinde = 0;
        int totalAusencias = 0;
        int totalLlegadasTarde = 0;
        int diasTrabajados = 0;
        int minutosTardeTotales = 0;
        int totalIncompletos = 0;
        int totalFaltasConAviso = 0;
        int totalAusenciasJustificadas = 0;
        int totalVacaciones = 0;
        int totalNoMarco = 0;

        // 1. Calcular horas de cada turno
        double horasTurno1 = Duration.between(empleado.getHoraEntrada(), empleado.getHoraSalida()).toMinutes() / 60.0;
        double horasTurno2 = 0.0;
        boolean tieneDosTurnos = false;

        if (empleado.getHoraEntrada2() != null && empleado.getHoraSalida2() != null) {
            horasTurno2 = Duration.between(empleado.getHoraEntrada2(), empleado.getHoraSalida2()).toMinutes() / 60.0;
            tieneDosTurnos = true;
        }

        // 2. Determinar si los turnos tienen la misma cantidad de horas
        boolean turnosIgualHoras = Math.abs(horasTurno1 - horasTurno2) < 0.01; // Comparación con margen de error

        LocalDate actual = desde;

        while (!actual.isAfter(hasta)) {
            String nombreDia = actual.getDayOfWeek().getDisplayName(TextStyle.FULL, localeEs);
            String fechaFormateada = actual.format(formatoLatino);

            List<RegistroAsistencia> marcasDelDia = registrosPorDia
                    .getOrDefault(actual, List.of())
                    .stream()
                    .sorted(Comparator.comparing(RegistroAsistencia::getHora))
                    .toList();

            boolean esFeriado = feriadoService.esFeriado(actual);
            boolean esFinDeSemana = actual.getDayOfWeek() == DayOfWeek.SATURDAY || actual.getDayOfWeek() == DayOfWeek.SUNDAY;


            // ---------- Verificar ausencia justificada ----------
            Ausencia ausenciaDia = null;
            boolean tieneAusenciaJustificada = false;
            boolean tieneNoMarco = false;
            boolean tieneFaltaConAviso = false;

            ausenciaDia = ausenciaService.obtenerAusenciaEmpleadoEnFecha(empleado, actual);
            if (ausenciaDia != null) {
                tieneAusenciaJustificada = (ausenciaDia.getTipoDeAusencia() == Ausencia.TipoDeAusencia.JUSTIFICADA
                        || ausenciaDia.getTipoDeAusencia() == Ausencia.TipoDeAusencia.VACACIONES);
                tieneNoMarco = (ausenciaDia.getTipoDeAusencia() == Ausencia.TipoDeAusencia.NO_MARCO);
                tieneFaltaConAviso = (ausenciaDia.getTipoDeAusencia() == Ausencia.TipoDeAusencia.FALTA_CON_AVISO);
            }

            boolean esLaboral = !esFeriado && !esFinDeSemana;
            boolean tieneHorasJustificadas = false;
            boolean tieneHorasReales = false;
            int numeroPar = 0; // Contador de pares de marcas
            boolean tieneMarcasIncompletas = false;

            // ---------- Manejo de marcas incompletas ----------
            if (!marcasDelDia.isEmpty() && marcasDelDia.size() % 2 != 0) {
                totalIncompletos++;
                tieneMarcasIncompletas = true;
                RegistroAsistencia marcaIncompleta = marcasDelDia.get(marcasDelDia.size() - 1);
                boolean faltaSalida = marcaIncompleta.getOrdenDia() % 2 == 1;

                String fotoBase64 = null;
                if (marcaIncompleta.getFoto() != null && marcaIncompleta.getFoto().length > 0) {
                    fotoBase64 = Base64.getEncoder().encodeToString(marcaIncompleta.getFoto()).replaceAll("\\s+", "");
                }

                ResumenEmpleadoDTO dtoIncompleto = new ResumenEmpleadoDTO();
                dtoIncompleto.setDni(empleado.getDni());
                dtoIncompleto.setNombreCompleto(empleado.getNombre() + " " + empleado.getApellido());
                dtoIncompleto.setFechaFormateada(fechaFormateada);
                dtoIncompleto.setNombreDia(nombreDia);
                dtoIncompleto.setMarcaIncompleta(true);
                dtoIncompleto.setTipoIncompleto(faltaSalida ? "FALTA_SALIDA" : "FALTA_ENTRADA");
                dtoIncompleto.setHoraEntrada(faltaSalida ? marcaIncompleta.getHora() : null);
                dtoIncompleto.setHoraSalida(faltaSalida ? null : marcaIncompleta.getHora());
                dtoIncompleto.setTipoHora("INCOMPLETO");
                dtoIncompleto.setHorasTrabajadas(0);
                dtoIncompleto.setAusente(false);
                dtoIncompleto.setEsFeriado(esFeriado);
                dtoIncompleto.setEsFinDeSemana(esFinDeSemana);
                dtoIncompleto.setJustificada(false);

                if (faltaSalida) {
                    dtoIncompleto.setFotoEntradaBase64(fotoBase64);
                    dtoIncompleto.setFotoSalidaBase64(null);
                } else {
                    dtoIncompleto.setFotoEntradaBase64(null);
                    dtoIncompleto.setFotoSalidaBase64(fotoBase64);
                }

                detalle.add(dtoIncompleto);
            }

            // Lista temporal para almacenar los DTOs del día antes de distribuir horas
            List<ResumenEmpleadoDTO> dtosDelDia = new ArrayList<>();
            double horasTrabajadasRealesDelDia = 0.0;
            List<Double> horasPorPar = new ArrayList<>();
            List<Boolean> esSegundoHorarioPorPar = new ArrayList<>();
            List<Boolean> esJustificadoPorPar = new ArrayList<>();

            // ---------- Procesamiento de TODOS los pares de marcas primero ----------
            for (int i = 0; i + 1 < marcasDelDia.size(); i += 2) {
                numeroPar++;
                RegistroAsistencia entrada = marcasDelDia.get(i);
                RegistroAsistencia salida = marcasDelDia.get(i + 1);

                String fotoEntradaBase64 = (entrada.getFoto() != null) ? Base64.getEncoder().encodeToString(entrada.getFoto()).replaceAll("\\s+", "") : null;
                String fotoSalidaBase64 = (salida.getFoto() != null) ? Base64.getEncoder().encodeToString(salida.getFoto()).replaceAll("\\s+", "") : null;

                double horas = Duration.between(entrada.getHora(), salida.getHora()).toMinutes() / 60.0;
                boolean esJustificada = entrada.getJustificacion() != null;

                // Determinar si este par pertenece al segundo horario
                boolean esSegundoHorario = false;
                if (tieneDosTurnos) {
                    long diffHorario1 = Math.abs(Duration.between(entrada.getHora(), empleado.getHoraEntrada()).toMinutes());
                    long diffHorario2 = Math.abs(Duration.between(entrada.getHora(), empleado.getHoraEntrada2()).toMinutes());
                    if (diffHorario2 < diffHorario1) {
                        esSegundoHorario = true;
                    }
                }

                // Solo verificar llegada tarde para el primer par del día
                boolean llegoTarde = false;
                int minutosTarde = 0;

                if (numeroPar == 1 && esLaboral && !esFeriado && !esFinDeSemana) {
                    int flexMinutos = Optional.ofNullable(empleado.getFlexMinutos()).orElse(0);
                    LocalTime horaEntradaActiva = esSegundoHorario ? empleado.getHoraEntrada2() : empleado.getHoraEntrada();
                    LocalTime horaEntradaMaxima = horaEntradaActiva.plusMinutes(flexMinutos);

                    if (entrada.getHora().isAfter(horaEntradaMaxima)) {
                        llegoTarde = true;
                        minutosTarde = (int) Duration.between(horaEntradaMaxima, entrada.getHora()).toMinutes();
                        totalLlegadasTarde++;
                        minutosTardeTotales += minutosTarde;
                    }
                }

                if (esJustificada) {
                    tieneHorasJustificadas = true;
                } else {
                    tieneHorasReales = true;
                    horasTrabajadasRealesDelDia += horas;
                }

                // Guardar datos para procesamiento posterior
                horasPorPar.add(horas);
                esSegundoHorarioPorPar.add(esSegundoHorario);
                esJustificadoPorPar.add(esJustificada);

                ResumenEmpleadoDTO dto = new ResumenEmpleadoDTO();
                dto.setDni(empleado.getDni());
                dto.setNombreCompleto(empleado.getNombre() + " " + empleado.getApellido());
                dto.setFechaFormateada(fechaFormateada);
                dto.setNombreDia(nombreDia);
                dto.setHoraEntrada(entrada.getHora());
                dto.setHoraSalida(salida.getHora());
                dto.setHorasTrabajadas(horas);
                dto.setMarcaIncompleta(false);
                dto.setAusente(false);
                dto.setLlegoTarde(llegoTarde);
                dto.setMinutosTarde(minutosTarde);
                dto.setEsFeriado(esFeriado);
                dto.setEsFinDeSemana(esFinDeSemana);
                dto.setJustificada(esJustificada);
                dto.setSegundoHorario(esSegundoHorario);
                dto.setFotoEntradaBase64(fotoEntradaBase64);
                dto.setFotoSalidaBase64(fotoSalidaBase64);

                // Si hay ausencia (justificada o no), marcamos el DTO
                if (ausenciaDia != null) {
                    dto.setTipoDeAusencia(ausenciaDia.getTipoDeAusencia());
                    dto.setJustificada(tieneAusenciaJustificada || tieneNoMarco || tieneFaltaConAviso);
                }

                dtosDelDia.add(dto);
            }

            // ---------- Calcular horas totales del día ----------
            double horasRealesTotales = 0;
            double horasTotalesDelDia = 0;

            if (!horasPorPar.isEmpty()) {
                horasTotalesDelDia = horasPorPar.stream().mapToDouble(Double::doubleValue).sum();

                // Calcular horas reales (no justificadas)
                for (int i = 0; i < dtosDelDia.size(); i++) {
                    if (!dtosDelDia.get(i).isJustificada()) {
                        horasRealesTotales += horasPorPar.get(i);
                    }
                }
            }

            // ---------- NUEVA LÓGICA: Determinar umbral para horas extras ----------
            boolean aplicarHorasExtras = false;
            double horasConsideradasParaCalculo = 0;
            double umbralHorasExtras = 0;

            if (esLaboral) {
                // Determinar qué horas considerar según el tipo de día
                if (tieneNoMarco) {
                    // Para NO_MARCO, considerar TODAS las horas trabajadas
                    horasConsideradasParaCalculo = horasTotalesDelDia;
                    totalNoMarco++;
                } else if (tieneAusenciaJustificada || tieneFaltaConAviso) {
                    // Para días JUSTIFICADOS o FALTA_CON_AVISO, no aplicar horas extras
                    aplicarHorasExtras = false;
                    horasConsideradasParaCalculo = horasRealesTotales;

                    // Contabilizar el tipo de ausencia
                    if (ausenciaDia != null) {
                        switch (ausenciaDia.getTipoDeAusencia()) {
                            case FALTA_CON_AVISO:
                                totalFaltasConAviso++;
                                break;
                            case VACACIONES:
                                totalVacaciones++;
                                break;
                            case JUSTIFICADA:
                                totalAusenciasJustificadas++;
                                break;
                        }
                    }
                } else {
                    // Días normales sin ausencia
                    horasConsideradasParaCalculo = horasRealesTotales;
                }

                // LÓGICA NUEVA: Determinar umbral según el patrón de trabajo del día
                if ((!tieneAusenciaJustificada && !tieneFaltaConAviso) || tieneNoMarco) {
                    // Analizar si trabajó en ambos turnos o solo en uno
                    boolean trabajoEnPrimerTurno = false;
                    boolean trabajoEnSegundoTurno = false;

                    for (int i = 0; i < dtosDelDia.size(); i++) {
                        if (tieneNoMarco || !esJustificadoPorPar.get(i)) {
                            if (esSegundoHorarioPorPar.get(i)) {
                                trabajoEnSegundoTurno = true;
                            } else {
                                trabajoEnPrimerTurno = true;
                            }
                        }
                    }

                    // CASO 1: Si los dos turnos tienen la misma cantidad de horas
                    if (turnosIgualHoras) {
                        if (trabajoEnPrimerTurno && !trabajoEnSegundoTurno) {
                            // Solo trabajó en el primer turno - usar solo ese turno
                            umbralHorasExtras = horasTurno1 + 0.5;
                        } else if (!trabajoEnPrimerTurno && trabajoEnSegundoTurno) {
                            // Solo trabajó en el segundo turno - usar solo ese turno
                            umbralHorasExtras = horasTurno2 + 0.5;
                        } else if (trabajoEnPrimerTurno && trabajoEnSegundoTurno) {
                            // Trabajó en ambos turnos - usar solo el primero (ya que son iguales)
                            umbralHorasExtras = horasTurno1 + 0.5;
                        }
                    }
                    // CASO 2: Los turnos tienen distinta cantidad de horas
                    else {
                        // Siempre sumar ambos turnos para el umbral
                        umbralHorasExtras = (horasTurno1 + horasTurno2) + 0.5;
                    }

                    // Aplicar horas extras si se supera el umbral
                    aplicarHorasExtras = horasConsideradasParaCalculo > umbralHorasExtras;
                }
            }
            // ---------- Distribuir horas normales y extras ----------
            if (aplicarHorasExtras && !dtosDelDia.isEmpty()) {
                // Calcular horas extras totales del día
                double horasExtrasTotales = Math.max(0, horasConsideradasParaCalculo - umbralHorasExtras + 0.5);
                double horasNormalesDelDia = horasConsideradasParaCalculo - horasExtrasTotales;

                // Identificar índices con horas a distribuir
                List<Integer> indicesParaDistribuir = new ArrayList<>();
                for (int i = 0; i < dtosDelDia.size(); i++) {
                    if (tieneNoMarco || !esJustificadoPorPar.get(i)) {
                        indicesParaDistribuir.add(i);
                    }
                }

                // Distribuir horas NORMALMENTE hasta llenar el turno
                double horasNormalesAsignadas = 0;

                for (int i = 0; i < indicesParaDistribuir.size(); i++) {
                    int idx = indicesParaDistribuir.get(i);
                    ResumenEmpleadoDTO dto = dtosDelDia.get(idx);
                    double horasPar = horasPorPar.get(idx);

                    if (horasNormalesAsignadas < horasNormalesDelDia) {
                        // Aún hay horas normales por asignar
                        double horasNormalesParaEstePar = Math.min(horasPar, horasNormalesDelDia - horasNormalesAsignadas);
                        double horasExtrasParaEstePar = horasPar - horasNormalesParaEstePar;

                        String tipoHora;
                        if (horasExtrasParaEstePar > 0 && horasNormalesParaEstePar > 0) {
                            tipoHora = "MIXTO";
                        } else if (horasExtrasParaEstePar > 0) {
                            tipoHora = "EXTRA";
                        } else {
                            tipoHora = "NORMAL";
                        }

                        dto.setTipoHora(tipoHora);
                        dto.setHorasNormales(horasNormalesParaEstePar);
                        dto.setHorasExtras(horasExtrasParaEstePar);

                        horasNormalesAsignadas += horasNormalesParaEstePar;
                        totalNormales += horasNormalesParaEstePar;
                        totalExtras += horasExtrasParaEstePar;
                        totalHoras += horasPar;

                    } else {
                        // Ya se asignaron todas las horas normales, el resto es extra puro
                        dto.setTipoHora("EXTRA");
                        dto.setHorasNormales(0.0);
                        dto.setHorasExtras(horasPar);

                        totalExtras += horasPar;
                        totalHoras += horasPar;
                    }
                }

                detalle.addAll(dtosDelDia);

            } else if (esFeriado || esFinDeSemana) {
                // Días feriados o fin de semana
                for (int i = 0; i < dtosDelDia.size(); i++) {
                    ResumenEmpleadoDTO dto = dtosDelDia.get(i);
                    double horas = horasPorPar.get(i);

                    String tipoHora = esFeriado ? "FERIADO" : "FIN_SEMANA";
                    dto.setTipoHora(tipoHora);
                    dto.setHorasNormales(0.0);
                    dto.setHorasExtras(0.0);

                    if (esFeriado || esFinDeSemana) {
                        totalFinde += horas;
                        totalHoras += horas;
                    }
                }
                detalle.addAll(dtosDelDia);
            } else if (!dtosDelDia.isEmpty()) {
                // Días sin horas extras - CORREGIDO PARA MANEJAR NO_MARCO
                for (int i = 0; i < dtosDelDia.size(); i++) {
                    ResumenEmpleadoDTO dto = dtosDelDia.get(i);
                    double horas = horasPorPar.get(i);

                    // CAMBIO PRINCIPAL: Si es NO_MARCO, siempre asignar como NORMAL
                    if (tieneNoMarco) {
                        // Para NO_MARCO, las horas trabajadas siempre son normales
                        dto.setTipoHora("NORMAL");
                        dto.setHorasNormales(horas);
                        dto.setHorasExtras(0.0);

                        // Sumar a los totales solo si no son justificadas
                        if (!esJustificadoPorPar.get(i)) {
                            totalNormales += horas;
                            totalHoras += horas;
                        }
                    }
                    // Para otros tipos de días (no justificados)
                    else if (!esJustificadoPorPar.get(i)) {
                        dto.setTipoHora("NORMAL");
                        dto.setHorasNormales(horas);
                        dto.setHorasExtras(0.0);

                        totalNormales += horas;
                        totalHoras += horas;
                    }
                    // Para horas justificadas (no NO_MARCO)
                    else {
                        dto.setTipoHora("JUSTIFICADA");
                        dto.setHorasNormales(0.0);
                        dto.setHorasExtras(0.0);
                    }
                }

                detalle.addAll(dtosDelDia);

            }

            // ---------- Día sin marcas ----------
            if (marcasDelDia.isEmpty() && !tieneMarcasIncompletas) {
                ResumenEmpleadoDTO dto = new ResumenEmpleadoDTO();
                dto.setDni(empleado.getDni());
                dto.setNombreCompleto(empleado.getNombre() + " " + empleado.getApellido());
                dto.setFechaFormateada(fechaFormateada);
                dto.setNombreDia(nombreDia);
                dto.setHorasTrabajadas(0);
                dto.setEsFeriado(esFeriado);
                dto.setEsFinDeSemana(esFinDeSemana);

                if (esLaboral) {
                    if (tieneAusenciaJustificada || tieneNoMarco || tieneFaltaConAviso) {
                        // CAMBIO AQUÍ: NO_MARCO siempre debe tener tipo de hora NORMAL
                        if (tieneNoMarco) {
                            dto.setTipoHora("NORMAL");  // Esto asegura que NO_MARCO muestre NORMAL
                        } else {
                            dto.setTipoHora("NORMAL");
                        }

                        dto.setAusente(false);
                        dto.setJustificada(true);

                        if (ausenciaDia != null) {
                            dto.setTipoDeAusencia(ausenciaDia.getTipoDeAusencia());

                            if (tieneNoMarco) {
                                // Calcular horas normales para NO_MARCO
                                double horasNormalesParaNoMarco = 0;

                                if (turnosIgualHoras) {
                                    // Si los turnos son iguales, considerar solo un turno
                                    horasNormalesParaNoMarco = horasTurno1;
                                } else {
                                    // Si los turnos son diferentes, sumar ambos
                                    horasNormalesParaNoMarco = horasTurno1 + horasTurno2;
                                }

                                dto.setHorasTrabajadas(horasNormalesParaNoMarco);
                                dto.setHorasNormales(horasNormalesParaNoMarco);
                                dto.setHorasExtras(0.0);
                                totalNormales += horasNormalesParaNoMarco;
                                totalHoras += horasNormalesParaNoMarco;
                                totalNoMarco++;

                            } else {
                                dto.setHorasTrabajadas(0);
                                dto.setHorasNormales(0.0);
                                dto.setHorasExtras(0.0);

                                switch (ausenciaDia.getTipoDeAusencia()) {
                                    case FALTA_CON_AVISO:
                                        totalFaltasConAviso++;
                                        break;
                                    case VACACIONES:
                                        totalVacaciones++;
                                        break;
                                    case JUSTIFICADA:
                                        totalAusenciasJustificadas++;
                                        break;
                                }
                            }
                        }
                    } else {
                        dto.setTipoHora("AUSENTE");
                        dto.setAusente(true);
                        dto.setJustificada(false);
                        dto.setTipoDeAusencia(Ausencia.TipoDeAusencia.FALTA_SIN_AVISO);
                        dto.setHorasNormales(0.0);
                        dto.setHorasExtras(0.0);
                        totalAusencias++;
                    }
                } else {
                    dto.setTipoHora(esFeriado ? "FERIADO" : "FIN_SEMANA");
                    dto.setAusente(false);
                    dto.setJustificada(false);
                    dto.setTipoDeAusencia(null);
                    dto.setHorasNormales(0.0);
                    dto.setHorasExtras(0.0);
                }

                detalle.add(dto);
            }

            boolean trabajoEseDia = tieneHorasReales || !marcasDelDia.isEmpty();
            if (trabajoEseDia && (ausenciaDia == null
                    || (ausenciaDia.getTipoDeAusencia() != Ausencia.TipoDeAusencia.FALTA_SIN_AVISO
                    && ausenciaDia.getTipoDeAusencia() != Ausencia.TipoDeAusencia.FALTA_CON_AVISO))) {
                diasTrabajados++;
            }

            actual = actual.plusDays(1);
        }

        // ---------- Cálculo de presentismo ----------
        boolean tieneAusenciaQueQuitaPresentismo = detalle.stream().anyMatch(dto -> {
            Ausencia.TipoDeAusencia tipo = dto.getTipoDeAusencia();
            return tipo != null
                    && tipo != Ausencia.TipoDeAusencia.VACACIONES
                    && tipo != Ausencia.TipoDeAusencia.NO_MARCO;
        });

        int totalMinutosTarde = detalle.stream().mapToInt(ResumenEmpleadoDTO::getMinutosTarde).sum();
        boolean presentismo = !(tieneAusenciaQueQuitaPresentismo || totalMinutosTarde > 30);

        detalle.forEach(dto -> dto.setPresentismo(presentismo));

        return detalle;
    }


    public Map<String, Object> generarDetalleEmpleadoView(String dni, LocalDate desde, LocalDate hasta) {
        Empleado empleado = empleadoRepo.findByDni(dni).orElseThrow();
        List<RegistroAsistencia> registros = registroRepository.findByEmpleadoDniAndFechaBetween(dni, desde, hasta);

        // DECIDIR QUÉ MÉTODO USAR SEGÚN EL ÁREA Y LA VARIABLE BOOLEANA
        List<ResumenEmpleadoDTO> detalle;
        boolean esAreaHogarAdultosMayores = empleado.getArea() != null &&
                "Hogar de Adultos Mayores".equals(empleado.getArea().getNombre());

        boolean usaLogicaEspecial = esAreaHogarAdultosMayores || empleado.isNoCumpleHorarioNormal();

        if (usaLogicaEspecial) {
            detalle = procesarAsistenciasTurnosAnormales(empleado, registros, desde, hasta);
        } else {
            detalle = procesarAsistenciasEmpleado(empleado, registros, desde, hasta);
        }

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("detalle", detalle);
        resultado.put("empleado", empleado);
        resultado.put("desde", desde);
        resultado.put("hasta", hasta);
        resultado.put("dniSeleccionado", dni);
        resultado.put("esTurnoNocturno", esAreaHogarAdultosMayores); // Agregar esta info

        // Filtramos registros válidos para los cálculos
        List<ResumenEmpleadoDTO> registrosValidos = detalle.stream()
                .filter(d -> !d.isMarcaIncompleta() &&
                        !"INVALIDO".equals(d.getTipoHora()))
                .toList();

        // Cálculo de totales CORREGIDO con redondeo a 0.5
        double totalHoras = redondearHoras(registrosValidos.stream()
                .mapToDouble(ResumenEmpleadoDTO::getHorasTrabajadas)
                .sum());

        // CORRECCIÓN: Cálculo simplificado y correcto de horas normales con redondeo
        double totalNormales = redondearHoras(registrosValidos.stream()
                .mapToDouble(d -> {
                    switch (d.getTipoHora()) {
                        case "NORMAL":
                            return d.getHorasTrabajadas();
                        case "MIXTO":
                            // Para registros mixtos, usar horasNormales directamente
                            // O calcular como: horas trabajadas - horas extras
                            return d.getHorasTrabajadas() - d.getHorasExtras();
                        case "EXTRA":
                        case "FERIADO":
                        case "FIN_SEMANA":
                        default:
                            return 0.0;
                    }
                })
                .sum());

        // CORRECCIÓN: Simplificar cálculo de horas extras con redondeo
        double totalExtras = redondearHoras(registrosValidos.stream()
                .filter(d ->
                        !"FIN_SEMANA".equals(d.getTipoHora()) &&
                                !"FERIADO".equals(d.getTipoHora())
                )
                .mapToDouble(ResumenEmpleadoDTO::getHorasExtras)
                .sum());

        double totalFinde = redondearHoras(registrosValidos.stream()
                .filter(d -> "FIN_SEMANA".equals(d.getTipoHora()) || "FERIADO".equals(d.getTipoHora()))
                .mapToDouble(ResumenEmpleadoDTO::getHorasTrabajadas)
                .sum());

        // NUEVOS TOTALES PARA AUSENCIAS
        long totalFaltasConAviso = detalle.stream()
                .filter(d -> d.getTipoDeAusencia() == Ausencia.TipoDeAusencia.FALTA_CON_AVISO)
                .count();

        long totalVacaciones = detalle.stream()
                .filter(d -> d.getTipoDeAusencia() == Ausencia.TipoDeAusencia.VACACIONES)
                .count();

        long totalAusenciasJustificadas = detalle.stream()
                .filter(d -> {
                    Ausencia.TipoDeAusencia tipo = d.getTipoDeAusencia();
                    return tipo != null &&
                            tipo != Ausencia.TipoDeAusencia.FALTA_SIN_AVISO &&
                            tipo != Ausencia.TipoDeAusencia.FALTA_CON_AVISO &&
                            tipo != Ausencia.TipoDeAusencia.VACACIONES &&
                            tipo != Ausencia.TipoDeAusencia.NO_MARCO;
                })
                .count();

        // Resto del código igual...
        List<ResumenEmpleadoDTO> ausencias = detalle.stream()
                .filter(d -> {
                    boolean esAusente = d.isAusente();
                    boolean noEsFeriado = !d.isEsFeriado();
                    boolean noEsFinDeSemana = !d.isEsFinDeSemana();
                    boolean esInjustificada = d.getTipoDeAusencia() == Ausencia.TipoDeAusencia.FALTA_SIN_AVISO;
                    return esAusente && noEsFeriado && noEsFinDeSemana && esInjustificada;
                })
                .toList();

        long totalAusencias = ausencias.size();

        long totalLlegadasTarde = detalle.stream()
                .filter(d -> d.isLlegoTarde() &&
                        !d.isEsFeriado() &&
                        !d.isEsFinDeSemana())
                .count();

        int diasTrabajados = (int) detalle.stream()
                .filter(d -> {
                    boolean tieneMarcas = d.getHoraEntrada() != null || d.getHoraSalida() != null;
                    boolean esValido = !d.isMarcaIncompleta() && !"INVALIDO".equals(d.getTipoHora());
                    boolean tieneHorasReales = d.getHorasTrabajadas() > 0 && !d.isJustificada();

                    boolean trabajoEseDia = tieneHorasReales || tieneMarcas;

                    // No debe tener faltas con aviso ni sin aviso
                    boolean sinFaltasInvalidas = d.getTipoDeAusencia() == null
                            || (d.getTipoDeAusencia() != Ausencia.TipoDeAusencia.FALTA_SIN_AVISO
                            && d.getTipoDeAusencia() != Ausencia.TipoDeAusencia.FALTA_CON_AVISO);

                    return esValido && trabajoEseDia && sinFaltasInvalidas;
                })
                .map(ResumenEmpleadoDTO::getFechaFormateada)
                .distinct()
                .count();

        int minutosTardeTotales = detalle.stream()
                .filter(d -> d.isLlegoTarde() && !d.isEsFeriado() && !d.isEsFinDeSemana())
                .mapToInt(ResumenEmpleadoDTO::getMinutosTarde)
                .sum();

        boolean presentismoEmpleado = !detalle.isEmpty() && detalle.get(0).isPresentismo();
        resultado.put("presentismoEmpleado", presentismoEmpleado);

        resultado.put("totalHoras", totalHoras);
        resultado.put("totalNormales", totalNormales);
        resultado.put("totalExtras", totalExtras);
        resultado.put("totalFindeFeriado", totalFinde);
        resultado.put("totalLlegadasTarde", totalLlegadasTarde);
        resultado.put("diasTrabajados", diasTrabajados);
        resultado.put("minutosTardeTotales", minutosTardeTotales);
        resultado.put("totalAusencias", totalAusencias);

        // AGREGAR LOS NUEVOS TOTALES AL RESULTADO
        resultado.put("totalFaltasConAviso", totalFaltasConAviso);
        resultado.put("totalVacaciones", totalVacaciones);
        resultado.put("totalAusenciasJustificadas", totalAusenciasJustificadas);

        resultado.put("detalle", detalle);

        return resultado;
    }

    /**
     * Método auxiliar para redondear horas a múltiplos de 0.5
     *
     * @param horas Horas a redondear
     * @return Horas redondeadas a 0.5
     */
    private double redondearHoras(double horas) {
        // Multiplicamos por 2 para trabajar en unidades de 0.5
        double multiplicado = horas * 2;
        // Redondeamos al entero más cercano
        double redondeado = Math.round(multiplicado);
        // Dividimos por 2 para volver a las unidades originales
        return redondeado / 2;
    }

    public List<ResumenAsistenciasDTO> generarResumenTotalesPorLog(Long logId, Empleado.TipoContrato tipoContrato) {

        LogCargaAsistencia log = logCargaRepository.findById(logId)
                .orElseThrow(() -> new RuntimeException("Log no encontrado"));

        LocalDate desde = log.getDesde();
        LocalDate hasta = log.getHasta();

        List<Empleado> empleados = tipoContrato != null
                ? empleadoRepo.findByTipoContrato(tipoContrato)
                : empleadoRepo.findAll();
        empleados.sort(Comparator.comparing(Empleado::getApellido));

        List<ResumenAsistenciasDTO> resumen = new ArrayList<>();

        for (Empleado empleado : empleados) {
            Map<String, Object> datos = generarDetalleEmpleadoView(empleado.getDni(), desde, hasta);
            List<ResumenEmpleadoDTO> detalle = (List<ResumenEmpleadoDTO>) datos.get("detalle");

            // Verificar si tiene al menos una marca incompleta
            boolean tieneMarcasIncompletas = detalle.stream()
                    .anyMatch(ResumenEmpleadoDTO::isMarcaIncompleta);

            // Convertir valores
            double totalHoras = datos.get("totalHoras") != null ? (double) datos.get("totalHoras") : 0.0;
            double totalNormales = datos.get("totalNormales") != null ? (double) datos.get("totalNormales") : 0.0;
            double totalExtras = datos.get("totalExtras") != null ? (double) datos.get("totalExtras") : 0.0;
            double totalFindeFeriado = datos.get("totalFindeFeriado") != null ? (double) datos.get("totalFindeFeriado") : 0.0;
            long totalAusencias = datos.get("totalAusencias") != null ? (long) datos.get("totalAusencias") : 0L;
            long totalLlegadasTarde = datos.get("totalLlegadasTarde") != null ? (long) datos.get("totalLlegadasTarde") : 0L;
            boolean presentismo = datos.get("presentismoEmpleado") != null && (boolean) datos.get("presentismoEmpleado");

            // NUEVOS TOTALES DE AUSENCIAS
            long totalFaltasConAviso = datos.get("totalFaltasConAviso") != null ? (long) datos.get("totalFaltasConAviso") : 0L;
            long totalVacaciones = datos.get("totalVacaciones") != null ? (long) datos.get("totalVacaciones") : 0L;
            long totalAusenciasJustificadas = datos.get("totalAusenciasJustificadas") != null ? (long) datos.get("totalAusenciasJustificadas") : 0L;

            // Actualizar el constructor de ResumenAsistenciasDTO para incluir los nuevos campos
            ResumenAsistenciasDTO dto = new ResumenAsistenciasDTO(
                    empleado.getDni(),
                    empleado.getNombre(),
                    empleado.getApellido(),
                    totalHoras,
                    totalNormales,
                    totalExtras,
                    totalFindeFeriado,
                    totalAusencias,
                    totalLlegadasTarde,
                    empleado.getTipoContrato(),
                    presentismo,
                    tieneMarcasIncompletas,
                    totalFaltasConAviso,      // Nuevo campo
                    totalVacaciones,          // Nuevo campo
                    totalAusenciasJustificadas // Nuevo campo
            );

            resumen.add(dto);
        }

        return resumen;
    }

    private RegistroAsistencia.TipoHora detectarTipoHora(Empleado emp, LocalDate fecha, int orden) {
        DayOfWeek dia = fecha.getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) {
            return RegistroAsistencia.TipoHora.FIN_SEMANA;
        }
        return orden < 2 ? RegistroAsistencia.TipoHora.NORMAL : RegistroAsistencia.TipoHora.EXTRA;

    }


//    @Transactional
//    public void editarHorariosDia(EdicionHorarioDTO dto) {
//        Empleado empleado = empleadoRepo.findByDni(dto.getDni())
//                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));
//
//        // Eliminar registros existentes
//        List<RegistroAsistencia> registros = registroRepository
//                .findByEmpleadoDniAndFecha(dto.getDni(), dto.getFecha());
//        registroRepository.deleteAll(registros);
//
//        // Validar y guardar dinámicamente
//        int totalHorarios = Math.min(dto.getEntradas().size(), dto.getSalidas().size());
//
//        for (int i = 0; i < totalHorarios; i++) {
//            LocalTime entrada = dto.getEntradas().get(i);
//            LocalTime salida = dto.getSalidas().get(i);
//
//            if (entrada != null && salida != null && entrada.isAfter(salida)) {
//                throw new IllegalArgumentException("La entrada " + (i + 1) + " no puede ser posterior a la salida");
//            }
//
//            RegistroAsistencia regEntrada = new RegistroAsistencia();
//            regEntrada.setEmpleado(empleado);
//            regEntrada.setFecha(dto.getFecha());
//            regEntrada.setHora(entrada);
//            regEntrada.setOrdenDia(i * 2 + 1);
//            regEntrada.setTipoHora(detectarTipoHora(empleado, dto.getFecha(), i * 2));
//            regEntrada.setMotivoCambio(dto.getMotivoCambio());
//
//            RegistroAsistencia regSalida = new RegistroAsistencia();
//            regSalida.setEmpleado(empleado);
//            regSalida.setFecha(dto.getFecha());
//            regSalida.setHora(salida);
//            regSalida.setOrdenDia(i * 2 + 2);
//            regSalida.setTipoHora(detectarTipoHora(empleado, dto.getFecha(), i * 2 + 1));
//            regSalida.setMotivoCambio(dto.getMotivoCambio());
//
//            registroRepository.save(regEntrada);
//            registroRepository.save(regSalida);
//        }
//    }

    @Transactional
    public void editarRegistrosAsistencia(EdicionHorarioDTO dto) {
        Empleado empleado = empleadoRepo.findByDni(dto.getDni())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        // Obtener registros existentes ordenados cronológicamente
        List<RegistroAsistencia> registrosExistentes = registroRepository
                .findByEmpleadoDniAndFecha(dto.getDni(), dto.getFecha());

        // ORDENAR LOS REGISTROS EXISTENTES CRONOLÓGICAMENTE
        registrosExistentes.sort(Comparator.comparing(RegistroAsistencia::getHora));

        // Crear mapa de registros existentes por ID para fácil acceso
        Map<Long, RegistroAsistencia> mapaExistentes = registrosExistentes.stream()
                .collect(Collectors.toMap(RegistroAsistencia::getId, Function.identity()));

        // Lista para nuevos registros
        List<RegistroAsistencia> nuevosRegistros = new ArrayList<>();

        // ORDENAR LOS REGISTROS DEL DTO CRONOLÓGICAMENTE
        List<RegistroDTO> registrosOrdenados = dto.getRegistros().stream()
                .filter(registro -> registro.getHora() != null)
                .sorted(Comparator.comparing(RegistroDTO::getHora))
                .collect(Collectors.toList());

        // Procesar cada registro del DTO (ahora ordenados)
        for (int i = 0; i < registrosOrdenados.size(); i++) {
            RegistroDTO registroDTO = registrosOrdenados.get(i);

            if (registroDTO.getId() != null && mapaExistentes.containsKey(registroDTO.getId())) {
                // Actualizar registro existente
                RegistroAsistencia registro = mapaExistentes.get(registroDTO.getId());
                registro.setHora(registroDTO.getHora());
                registro.setTipo(registroDTO.getTipo());
                registro.setOrdenDia(i);
                registro.setMotivoCambio(dto.getMotivoCambio());

                // Marcar como procesado
                mapaExistentes.remove(registroDTO.getId());
            } else {
                // Crear nuevo registro
                RegistroAsistencia nuevoRegistro = new RegistroAsistencia();
                nuevoRegistro.setEmpleado(empleado);
                nuevoRegistro.setFecha(dto.getFecha());
                nuevoRegistro.setHora(registroDTO.getHora());
                nuevoRegistro.setTipo(registroDTO.getTipo());
                nuevoRegistro.setOrdenDia(i);
                nuevoRegistro.setMotivoCambio(dto.getMotivoCambio());

                nuevosRegistros.add(nuevoRegistro);
            }
        }

        // Eliminar registros que no están en el DTO (fueron removidos por el usuario)
        if (!mapaExistentes.isEmpty()) {
            registroRepository.deleteAll(mapaExistentes.values());
        }

        // Guardar nuevos registros
        if (!nuevosRegistros.isEmpty()) {
            registroRepository.saveAll(nuevosRegistros);
        }
    }

    public EdicionHorarioDTO prepararEdicionHorario(String dni, LocalDate fecha) {
        EdicionHorarioDTO dto = new EdicionHorarioDTO();
        dto.setDni(dni);
        dto.setFecha(fecha);

        List<RegistroAsistencia> registros = registroRepository
                .findByEmpleadoDniAndFecha(dni, fecha)
                .stream()
                // ORDENAR CRONOLÓGICAMENTE POR HORA (más antiguo a más nuevo)
                .sorted(Comparator.comparing(RegistroAsistencia::getHora))
                .collect(Collectors.toList());

        // Convertir registros a DTOs
        List<RegistroDTO> registroDTOs = registros.stream()
                .map(registro -> new RegistroDTO(
                        registro.getId(),
                        registro.getHora(),
                        registro.getTipo()
                ))
                .collect(Collectors.toList());

        dto.setRegistros(registroDTOs);
        return dto;
    }

    public List<ResumenAsistenciasDTO> generarResumenTotalesPorFechas(LocalDate desde, LocalDate hasta, Empleado.TipoContrato tipoContrato) {

        List<Empleado> empleados = tipoContrato != null
                ? empleadoRepo.findByTipoContrato(tipoContrato)
                : empleadoRepo.findAll();
        empleados.sort(Comparator.comparing(Empleado::getApellido));

        List<ResumenAsistenciasDTO> resumen = new ArrayList<>();

        for (Empleado empleado : empleados) {
            Map<String, Object> datos = generarDetalleEmpleadoView(empleado.getDni(), desde, hasta);
            List<ResumenEmpleadoDTO> detalle = (List<ResumenEmpleadoDTO>) datos.get("detalle");

            boolean tieneMarcasIncompletas = detalle.stream()
                    .anyMatch(ResumenEmpleadoDTO::isMarcaIncompleta);

            double totalHoras = datos.get("totalHoras") != null ? (double) datos.get("totalHoras") : 0.0;
            double totalNormales = datos.get("totalNormales") != null ? (double) datos.get("totalNormales") : 0.0;
            double totalExtras = datos.get("totalExtras") != null ? (double) datos.get("totalExtras") : 0.0;
            double totalFindeFeriado = datos.get("totalFindeFeriado") != null ? (double) datos.get("totalFindeFeriado") : 0.0;
            long totalAusencias = datos.get("totalAusencias") != null ? (long) datos.get("totalAusencias") : 0L;
            long totalLlegadasTarde = datos.get("totalLlegadasTarde") != null ? (long) datos.get("totalLlegadasTarde") : 0L;
            boolean presentismo = datos.get("presentismoEmpleado") != null && (boolean) datos.get("presentismoEmpleado");
            // NUEVOS TOTALES DE AUSENCIAS
            long totalFaltasConAviso = datos.get("totalFaltasConAviso") != null ? (long) datos.get("totalFaltasConAviso") : 0L;
            long totalVacaciones = datos.get("totalVacaciones") != null ? (long) datos.get("totalVacaciones") : 0L;
            long totalAusenciasJustificadas = datos.get("totalAusenciasJustificadas") != null ? (long) datos.get("totalAusenciasJustificadas") : 0L;

            ResumenAsistenciasDTO dto = new ResumenAsistenciasDTO(
                    empleado.getDni(),
                    empleado.getNombre(),
                    empleado.getApellido(),
                    totalHoras,
                    totalNormales,
                    totalExtras,
                    totalFindeFeriado,
                    totalAusencias,
                    totalLlegadasTarde,
                    empleado.getTipoContrato(),
                    presentismo,
                    tieneMarcasIncompletas,
                    totalFaltasConAviso,      // Nuevo campo
                    totalVacaciones,          // Nuevo campo
                    totalAusenciasJustificadas // Nuevo campo
            );

            resumen.add(dto);
        }

        return resumen;
    }

    public List<ResumenAsistenciasDTO> generarResumenTotalesPorRango(
            LocalDate desde,
            LocalDate hasta,
            Empleado.TipoContrato tipoContrato) {

        return generarResumenTotalesPorFechas(desde, hasta, tipoContrato);
    }


    private String convertToBase64(byte[] imageBytes) {
        if (imageBytes != null && imageBytes.length > 0) {
            return Base64.getEncoder().encodeToString(imageBytes);
        }
        return null;
    }
}