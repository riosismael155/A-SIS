package com.asis.service;

import com.asis.model.*;
import com.asis.model.dto.CargaAsistenciaDTO;
import com.asis.model.dto.EdicionHorarioDTO;
import com.asis.model.dto.ResumenAsistenciasDTO;
import com.asis.model.dto.ResumenEmpleadoDTO;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExcelService {

    private final EmpleadoRepository empleadoRepo;
    private final RegistroRepository registroRepository;
    private final JustificacionAusenciaService ausenciaService;
    private final LogCargaAsistenciaRepository logCargaRepository;
    private final FeriadoService feriadoService;


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
        log.setFechaCarga(LocalDateTime.now());
        log.setCantidadRegistros(registros.size());

        logCargaRepository.save(log);
    }

    public String generarDescripcion(LocalDate desde, LocalDate hasta) {
        DateTimeFormatter mesFormatter = DateTimeFormatter.ofPattern("MMMM", new Locale("es", "ES"));
        String mes = desde.format(mesFormatter);
        return "Mes de " + capitalize(mes) + " " + desde.getYear();
    }

    private String capitalize(String texto) {
        if (texto == null || texto.isEmpty()) return texto;
        return texto.substring(0, 1).toUpperCase() + texto.substring(1);
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
            boolean esLaboral = !esFeriado && !esFinDeSemana;

            // ---------- Verificar ausencia justificada ----------
            Ausencia ausenciaDia = null;
            boolean tieneAusenciaJustificada = false;

            if (esLaboral) {
                ausenciaDia = ausenciaService.obtenerAusenciaEmpleadoEnFecha(empleado, actual);
                if (ausenciaDia != null) {
                    // Considerar como justificadas: VACACIONES, JUSTIFICADA, LICENCIA, etc. (todo excepto INJUSTIFICADA y NO_MARCO)
                    tieneAusenciaJustificada = (ausenciaDia.getTipoDeAusencia() != Ausencia.TipoDeAusencia.INJUSTIFICADA
                            && ausenciaDia.getTipoDeAusencia() != Ausencia.TipoDeAusencia.NO_MARCO);
                }
            }

            boolean tieneHorasJustificadas = false;
            boolean tieneHorasReales = false;
            boolean esPrimeraMarca = true;
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

            // ---------- Configuración de horario activo ----------
            LocalTime horaEntradaActiva = empleado.getHoraEntrada();
            LocalTime horaSalidaActiva = empleado.getHoraSalida();
            boolean esSegundoHorario = false;

            if (empleado.getHoraEntrada2() != null && empleado.getHoraSalida2() != null && !marcasDelDia.isEmpty()) {
                RegistroAsistencia primeraEntrada = marcasDelDia.get(0);
                long diffHorario1 = Math.abs(Duration.between(primeraEntrada.getHora(), empleado.getHoraEntrada()).toMinutes());
                long diffHorario2 = Math.abs(Duration.between(primeraEntrada.getHora(), empleado.getHoraEntrada2()).toMinutes());
                if (diffHorario2 < diffHorario1) {
                    horaEntradaActiva = empleado.getHoraEntrada2();
                    horaSalidaActiva = empleado.getHoraSalida2();
                    esSegundoHorario = true;
                }
            }

            int flexMinutos = Optional.ofNullable(empleado.getFlexMinutos()).orElse(0);
            LocalTime horaEntradaMaxima = horaEntradaActiva.plusMinutes(flexMinutos);
            LocalTime horaEntradaMinima = horaEntradaActiva.minusMinutes(flexMinutos);
            LocalTime horaSalidaMinima = horaSalidaActiva.minusMinutes(flexMinutos);
            LocalTime horaSalidaMaxima = horaSalidaActiva.plusMinutes(flexMinutos);

            // ---------- Procesamiento de pares de marcas ----------
            for (int i = 0; i + 1 < marcasDelDia.size(); i += 2) {
                RegistroAsistencia entrada = marcasDelDia.get(i);
                RegistroAsistencia salida = marcasDelDia.get(i + 1);

                String fotoEntradaBase64 = (entrada.getFoto() != null) ? Base64.getEncoder().encodeToString(entrada.getFoto()).replaceAll("\\s+", "") : null;
                String fotoSalidaBase64 = (salida.getFoto() != null) ? Base64.getEncoder().encodeToString(salida.getFoto()).replaceAll("\\s+", "") : null;

                double horas = Duration.between(entrada.getHora(), salida.getHora()).toMinutes() / 60.0;
                boolean esJustificada = entrada.getJustificacion() != null;

                boolean llegoTarde = false;
                int minutosTarde = 0;
                String tipoHora;

                double horasNormales = 0;
                double horasExtras = 0;
                double horasFinde = 0;

                if (esFeriado || esFinDeSemana) {
                    tipoHora = esFeriado ? "FERIADO" : "FIN_SEMANA";
                    horasFinde = horas;
                    totalFinde += horasFinde;
                } else {
                    if (i == 0 && entrada.getHora().isAfter(horaEntradaMaxima)) {
                        llegoTarde = true;
                        minutosTarde = (int) Duration.between(horaEntradaMaxima, entrada.getHora()).toMinutes();
                        totalLlegadasTarde++;
                        minutosTardeTotales += minutosTarde;
                    }

                    LocalTime inicioHorasNormales = entrada.getHora().isBefore(horaEntradaMinima) ? horaEntradaMinima : entrada.getHora();
                    LocalTime finHorasNormales = salida.getHora().isAfter(horaSalidaMaxima) ? horaSalidaMaxima : salida.getHora();

                    if (inicioHorasNormales.isBefore(finHorasNormales)) {
                        horasNormales = Duration.between(inicioHorasNormales, finHorasNormales).toMinutes() / 60.0;
                    }

                    if (entrada.getHora().isBefore(horaEntradaMinima)) {
                        horasExtras += Duration.between(entrada.getHora(), horaEntradaMinima).toMinutes() / 60.0;
                    }
                    if (salida.getHora().isAfter(horaSalidaMaxima)) {
                        horasExtras += Duration.between(horaSalidaMaxima, salida.getHora()).toMinutes() / 60.0;
                    }

                    tipoHora = esPrimeraMarca ? (horasExtras > 0 ? "MIXTO" : "NORMAL") : "EXTRA";

                    totalNormales += horasNormales;
                    totalExtras += horasExtras;
                }

                totalHoras += (horasNormales + horasExtras + horasFinde);

                if (esJustificada) {
                    tieneHorasJustificadas = true;
                } else {
                    tieneHorasReales = true;
                }

                ResumenEmpleadoDTO dto = new ResumenEmpleadoDTO();
                dto.setDni(empleado.getDni());
                dto.setNombreCompleto(empleado.getNombre() + " " + empleado.getApellido());
                dto.setFechaFormateada(fechaFormateada);
                dto.setNombreDia(nombreDia);
                dto.setHoraEntrada(entrada.getHora());
                dto.setHoraSalida(salida.getHora());
                dto.setTipoHora(tipoHora);
                dto.setHorasTrabajadas(horasNormales + horasExtras + horasFinde);
                dto.setMarcaIncompleta(false);
                dto.setAusente(false); // No es ausente porque tiene marcas
                dto.setLlegoTarde(llegoTarde);
                dto.setMinutosTarde(minutosTarde);
                dto.setEsFeriado(esFeriado);
                dto.setEsFinDeSemana(esFinDeSemana);
                dto.setJustificada(esJustificada);
                dto.setSegundoHorario(esSegundoHorario);
                dto.setHorasExtras(horasExtras);
                dto.setFotoEntradaBase64(fotoEntradaBase64);
                dto.setFotoSalidaBase64(fotoSalidaBase64);

                // Si hay ausencia (justificada o no), marcamos el DTO para mostrar el tipo
                if (ausenciaDia != null) {
                    dto.setTipoDeAusencia(ausenciaDia.getTipoDeAusencia()); // ← SIEMPRE mostrar el tipo
                    dto.setJustificada(tieneAusenciaJustificada);
                    // No incrementamos totalAusencias si es justificada

                }

                detalle.add(dto);
                esPrimeraMarca = false;
            }

            // ---------- Día sin marcas ----------
            if (marcasDelDia.isEmpty()) {
                ResumenEmpleadoDTO dto = new ResumenEmpleadoDTO();
                dto.setDni(empleado.getDni());
                dto.setNombreCompleto(empleado.getNombre() + " " + empleado.getApellido());
                dto.setFechaFormateada(fechaFormateada);
                dto.setNombreDia(nombreDia);
                dto.setHorasTrabajadas(0);
                dto.setEsFeriado(esFeriado);
                dto.setEsFinDeSemana(esFinDeSemana);

                if (esLaboral) {
                    if (tieneAusenciaJustificada) {
                        // Día con ausencia justificada (vacaciones, licencia, etc.) - considerar como horario normal
                        dto.setTipoHora("NORMAL");
                        dto.setAusente(false); // No es ausente porque está justificado
                        dto.setJustificada(true);
                        dto.setTipoDeAusencia(ausenciaDia.getTipoDeAusencia()); // ← MOSTRAR EL TIPO DE AUSENCIA

                        // Calcular horas normales como si hubiera trabajado el horario completo
                        double horasTrabajadasNormales = Duration.between(horaEntradaActiva, horaSalidaActiva).toMinutes() / 60.0;
                        dto.setHorasTrabajadas(horasTrabajadasNormales);
                        totalNormales += horasTrabajadasNormales;
                        totalHoras += horasTrabajadasNormales;
                    } else {
                        // Día sin marcas y sin ausencia justificada - es ausencia real
                        dto.setTipoHora("AUSENTE");
                        dto.setAusente(true);
                        dto.setJustificada(false);
                        dto.setTipoDeAusencia(Ausencia.TipoDeAusencia.INJUSTIFICADA);
                        totalAusencias++;
                    }
                } else {
                    dto.setTipoHora(esFeriado ? "FERIADO" : "FIN_SEMANA");
                    dto.setAusente(false);
                    dto.setJustificada(false);
                    dto.setTipoDeAusencia(null);
                }

                // SIEMPRE establecer el tipo de ausencia si existe, incluso para días justificados
                if (ausenciaDia != null) {
                    dto.setTipoDeAusencia(ausenciaDia.getTipoDeAusencia()); // ← ESTA LÍNEA ES CLAVE
                }

                detalle.add(dto);
            }

            boolean trabajoEseDia = tieneHorasReales || tieneHorasJustificadas || !marcasDelDia.isEmpty() || tieneAusenciaJustificada;
            if (trabajoEseDia && !tieneMarcasIncompletas) {
                diasTrabajados++;
            }

            actual = actual.plusDays(1);
        }

        // ---------- Cálculo de presentismo ----------
        boolean tieneInjustificadas = detalle.stream().anyMatch(dto ->
                dto.isAusente() && dto.getTipoDeAusencia() == Ausencia.TipoDeAusencia.INJUSTIFICADA);

        int totalMinutosTarde = detalle.stream().mapToInt(ResumenEmpleadoDTO::getMinutosTarde).sum();

        // Solo las injustificadas y los retardos > 30 minutos afectan al presentismo
        boolean presentismo = !(tieneInjustificadas || totalMinutosTarde > 30);

        detalle.forEach(dto -> dto.setPresentismo(presentismo));

        return detalle;
    }


    public Map<String, Object> generarDetalleEmpleadoView(String dni, LocalDate desde, LocalDate hasta) {
        Empleado empleado = empleadoRepo.findByDni(dni).orElseThrow();
        List<RegistroAsistencia> registros = registroRepository.findByEmpleadoDniAndFechaBetween(dni, desde, hasta);

        List<ResumenEmpleadoDTO> detalle = procesarAsistenciasEmpleado(empleado, registros, desde, hasta);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("detalle", detalle);
        resultado.put("empleado", empleado);
        resultado.put("desde", desde);
        resultado.put("hasta", hasta);
        resultado.put("dniSeleccionado", dni);

        // Filtramos registros válidos para los cálculos
        List<ResumenEmpleadoDTO> registrosValidos = detalle.stream()
                .filter(d -> !d.isMarcaIncompleta() &&
                        !"INVALIDO".equals(d.getTipoHora()))
                .toList();

        // Cálculo de totales (optimizado)
        double totalHoras = registrosValidos.stream()
                .mapToDouble(ResumenEmpleadoDTO::getHorasTrabajadas)
                .sum();

        double totalNormales = registrosValidos.stream()
                .mapToDouble(d -> {
                    if ("NORMAL".equals(d.getTipoHora())) {
                        return d.getHorasTrabajadas();
                    } else if ("MIXTO".equals(d.getTipoHora())) {
                        return d.getHorasTrabajadas() - (d.getDespuesDeHora() / 60.0);
                    }
                    return 0;
                })
                .sum();

        double totalExtras = registrosValidos.stream()
                .mapToDouble(ResumenEmpleadoDTO::getHorasExtras)
                .sum();

        double totalFinde = registrosValidos.stream()
                .filter(d -> "FIN_SEMANA".equals(d.getTipoHora()) || "FERIADO".equals(d.getTipoHora()))
                .mapToDouble(ResumenEmpleadoDTO::getHorasTrabajadas)
                .sum();

        // Total ausencias (solo días laborales)
        List<ResumenEmpleadoDTO> ausencias = detalle.stream()
                .filter(d -> {
                    boolean esAusente = d.isAusente();
                    boolean noEsFeriado = !d.isEsFeriado();
                    boolean noEsFinDeSemana = !d.isEsFinDeSemana();
                    boolean esInjustificada = d.getTipoDeAusencia() == Ausencia.TipoDeAusencia.INJUSTIFICADA;
                    return esAusente && noEsFeriado && noEsFinDeSemana && esInjustificada;
                })
                .toList();

        long totalAusencias = ausencias.size();


        // Total llegadas tarde (solo días laborales, en cualquier horario)
        long totalLlegadasTarde = detalle.stream()
                .filter(d -> d.isLlegoTarde() &&
                        !d.isEsFeriado() &&
                        !d.isEsFinDeSemana())
                .count();

        // Días trabajados (solo días con marcas válidas)
        int diasTrabajados = (int) detalle.stream()
                .filter(d -> {
                    boolean tieneMarcas = d.getHoraEntrada() != null || d.getHoraSalida() != null;
                    boolean esValido = !d.isMarcaIncompleta() && !"INVALIDO".equals(d.getTipoHora());
                    return tieneMarcas && esValido;
                })
                .map(ResumenEmpleadoDTO::getFechaFormateada)
                .distinct()
                .count();

        int minutosTardeTotales = detalle.stream()
                .filter(d -> d.isLlegoTarde() && !d.isEsFeriado() && !d.isEsFinDeSemana())
                .mapToInt(ResumenEmpleadoDTO::getMinutosTarde) // corregido, no getMinutosTardeTotales()
                .sum();

        // Presentismo del empleado (si querés resumido, basta con tomar el primer dto, ya que todos tienen el mismo valor)
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
        resultado.put("detalle", detalle); // Lista<ResumenEmpleadoDTO> con las fotos en Base64


        return resultado;
    }

    public List<ResumenAsistenciasDTO> generarResumenTotalesPorLog(Long logId, Empleado.TipoContrato tipoContrato) {
        LogCargaAsistencia log = logCargaRepository.findById(logId)
                .orElseThrow(() -> new RuntimeException("Log no encontrado"));

        LocalDate desde = log.getDesde();
        LocalDate hasta = log.getHasta();

        List<Empleado> empleados = tipoContrato != null
                ? empleadoRepo.findByTipoContrato(tipoContrato)
                : empleadoRepo.findAll();

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
            boolean presentismo = datos.get("presentismoEmpleado") != null ? (boolean) datos.get("presentismoEmpleado") : false;

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
                    tieneMarcasIncompletas  // Nuevo campo
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


    @Transactional
    public void editarHorariosDia(EdicionHorarioDTO edicionDTO) {
        // Validaciones básicas
        if (edicionDTO.getHoraEntrada1() != null && edicionDTO.getHoraSalida1() != null &&
                edicionDTO.getHoraEntrada1().isAfter(edicionDTO.getHoraSalida1())) {
            throw new IllegalArgumentException("La hora de entrada principal no puede ser posterior a la de salida");
        }

        if (edicionDTO.getHoraEntrada2() != null && edicionDTO.getHoraSalida2() != null &&
                edicionDTO.getHoraEntrada2().isAfter(edicionDTO.getHoraSalida2())) {
            throw new IllegalArgumentException("La hora de entrada secundaria no puede ser posterior a la de salida");
        }

        Empleado empleado = empleadoRepo.findByDni(edicionDTO.getDni())
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        // Eliminar registros existentes para esa fecha
        List<RegistroAsistencia> registros = registroRepository
                .findByEmpleadoDniAndFecha(edicionDTO.getDni(), edicionDTO.getFecha());
        registroRepository.deleteAll(registros);

        // Crear registros solo para los horarios proporcionados
        if (edicionDTO.getHoraEntrada1() != null && edicionDTO.getHoraSalida1() != null) {
            RegistroAsistencia entrada1 = new RegistroAsistencia();
            entrada1.setEmpleado(empleado);
            entrada1.setFecha(edicionDTO.getFecha());
            entrada1.setHora(edicionDTO.getHoraEntrada1());
            entrada1.setOrdenDia(1);
            entrada1.setTipoHora(detectarTipoHora(empleado, edicionDTO.getFecha(), 0));
            entrada1.setMotivoCambio(edicionDTO.getMotivoCambio());

            RegistroAsistencia salida1 = new RegistroAsistencia();
            salida1.setEmpleado(empleado);
            salida1.setFecha(edicionDTO.getFecha());
            salida1.setHora(edicionDTO.getHoraSalida1());
            salida1.setOrdenDia(2);
            salida1.setTipoHora(detectarTipoHora(empleado, edicionDTO.getFecha(), 1));
            salida1.setMotivoCambio(edicionDTO.getMotivoCambio());

            registroRepository.save(entrada1);
            registroRepository.save(salida1);
        }

        // Crear registros para el segundo horario (si existe)
        if (edicionDTO.getHoraEntrada2() != null && edicionDTO.getHoraSalida2() != null) {
            RegistroAsistencia entrada2 = new RegistroAsistencia();
            entrada2.setEmpleado(empleado);
            entrada2.setFecha(edicionDTO.getFecha());
            entrada2.setHora(edicionDTO.getHoraEntrada2());
            entrada2.setOrdenDia(3);
            entrada2.setTipoHora(detectarTipoHora(empleado, edicionDTO.getFecha(), 2));
            entrada2.setMotivoCambio(edicionDTO.getMotivoCambio());

            RegistroAsistencia salida2 = new RegistroAsistencia();
            salida2.setEmpleado(empleado);
            salida2.setFecha(edicionDTO.getFecha());
            salida2.setHora(edicionDTO.getHoraSalida2());
            salida2.setOrdenDia(4);
            salida2.setTipoHora(detectarTipoHora(empleado, edicionDTO.getFecha(), 3));
            salida2.setMotivoCambio(edicionDTO.getMotivoCambio());

            registroRepository.save(entrada2);
            registroRepository.save(salida2);
        }
    }

    public EdicionHorarioDTO prepararEdicionHorario(String dni, LocalDate fecha) {
        Empleado empleado = empleadoRepo.findByDni(dni)
                .orElseThrow(() -> new RuntimeException("Empleado no encontrado"));

        // Obtener registros existentes para esa fecha
        List<RegistroAsistencia> registros = registroRepository
                .findByEmpleadoDniAndFecha(dni, fecha);

        // Ordenar por orden del día
        registros = registros.stream()
                .sorted(Comparator.comparing(RegistroAsistencia::getOrdenDia))
                .collect(Collectors.toList());

        EdicionHorarioDTO dto = new EdicionHorarioDTO();
        dto.setDni(dni);
        dto.setFecha(fecha);

        // Si no hay registros, devolver DTO vacío
        if (registros.isEmpty()) {
            return dto;
        }

        // Procesar registros existentes
        for (int i = 0; i < registros.size(); i++) {
            RegistroAsistencia registro = registros.get(i);

            switch (i) {
                case 0: // Primera entrada
                    dto.setHoraEntrada1(registro.getHora());
                    break;
                case 1: // Primera salida
                    dto.setHoraSalida1(registro.getHora());
                    break;
                case 2: // Segunda entrada (si existe)
                    dto.setHoraEntrada2(registro.getHora());
                    break;
                case 3: // Segunda salida (si existe)
                    dto.setHoraSalida2(registro.getHora());
                    break;
            }
        }

        return dto;
    }

    private String convertToBase64(byte[] imageBytes) {
        if (imageBytes != null && imageBytes.length > 0) {
            return Base64.getEncoder().encodeToString(imageBytes);
        }
        return null;
    }
}

