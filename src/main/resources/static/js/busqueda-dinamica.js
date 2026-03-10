document.addEventListener("DOMContentLoaded", function () {
    const inputEmpleado = document.getElementById("empleado");
    const inputDni = document.getElementById("dni") || document.getElementById("dniBusqueda");
    const sugerencias = document.getElementById("sugerencias");

    inputEmpleado.addEventListener("input", function () {
        const query = this.value.trim();

        if (query.length < 2) {
            sugerencias.innerHTML = "";
            sugerencias.style.display = "none"; // Ocultar si no hay suficientes caracteres
            return;
        }

        fetch(`/empleados/buscar?q=${encodeURIComponent(query)}`)
            .then(response => response.json())
            .then(data => {
                sugerencias.innerHTML = "";

                if (data.length > 0) {
                    data.forEach(e => sugerencias.appendChild(crearSugerenciaEmpleado(e)));
                    sugerencias.style.display = "block"; // Mostrar sugerencias
                } else {
                    sugerencias.style.display = "none"; // Ocultar si no hay resultados
                }
            })
            .catch(error => {
                console.error("Error al buscar empleados:", error);
                sugerencias.innerHTML = "";
                sugerencias.style.display = "none";
            });
    });

    function crearSugerenciaEmpleado(e) {
        const item = document.createElement("a");
        item.href = "#";
        item.className = "list-group-item list-group-item-action";
        item.dataset.dni = e.dni;
        item.textContent = `${e.apellido} ${e.nombre} - ${e.dni}`;

        item.addEventListener("click", function (event) {
            event.preventDefault();
            inputEmpleado.value = this.textContent;
            if (inputDni) inputDni.value = this.dataset.dni;
            sugerencias.innerHTML = "";
            sugerencias.style.display = "none";

            // Solo hacemos fetch si existe el formulario de edición
            if (document.getElementById("formEditarEmpleado")) {
                fetch(`/empleados/${this.dataset.dni}`)
                    .then(response => response.json())
                    .then(e => setearDatosEmpleado(e))
                    .catch(error => {
                        console.error("Error al obtener datos del empleado:", error);
                    });
            }
        });

        return item;
    }

function setearDatosEmpleado(e) {
    const nombreInput = document.getElementById("nombre");
    const apellidoInput = document.getElementById("apellido");
    const dniInput = document.getElementById("dni");
    const horaEntradaInput = document.getElementById("horaEntrada");
    const horaSalidaInput = document.getElementById("horaSalida");
    const flexMinutosInput = document.getElementById("flexMinutos");
    const hiddenIdInput = document.querySelector("#formEditarEmpleado input[name='id']");
    const areaSelect = document.getElementById("area");
    const tipoContratoSelect = document.getElementById("tipoContrato");

    // Horarios secundarios
    const horaEntrada2Input = document.getElementById("horaEntrada2");
    const horaSalida2Input = document.getElementById("horaSalida2");
    const horaEntrada3Input = document.getElementById("horaEntrada3");
    const horaSalida3Input = document.getElementById("horaSalida3");
    const horaEntrada4Input = document.getElementById("horaEntrada4");
    const horaSalida4Input = document.getElementById("horaSalida4");

    // ✅ CORREGIDO: Checkbox para no cumple horario normal
    const noCumpleHorarioNormalCheckbox = document.getElementById("noCumpleHorarioNormal");

    if (nombreInput) nombreInput.value = e.nombre || "";
    if (apellidoInput) apellidoInput.value = e.apellido || "";
    if (dniInput) dniInput.value = e.dni || "";

    if (horaEntradaInput) horaEntradaInput.value = e.horaEntrada || "";
    if (horaSalidaInput) horaSalidaInput.value = e.horaSalida || "";

    if (horaEntrada2Input) horaEntrada2Input.value = e.horaEntrada2 || "";
    if (horaSalida2Input) horaSalida2Input.value = e.horaSalida2 || "";

    if (horaEntrada3Input) horaEntrada3Input.value = e.horaEntrada3 || "";
    if (horaSalida3Input) horaSalida3Input.value = e.horaSalida3 || "";

    if (horaEntrada4Input) horaEntrada4Input.value = e.horaEntrada4 || "";
    if (horaSalida4Input) horaSalida4Input.value = e.horaSalida4 || "";

    // ✅ CORREGIDO: Setear el estado del checkbox
    if (noCumpleHorarioNormalCheckbox) {
        noCumpleHorarioNormalCheckbox.checked = e.noCumpleHorarioNormal || false;
    }

    if (flexMinutosInput) flexMinutosInput.value = e.flexMinutos || "";
    if (hiddenIdInput) hiddenIdInput.value = e.id || "";

    // Área
    if (areaSelect && e.areaId) {
        areaSelect.value = e.areaId;
    }

    // Tipo de contrato
    if (tipoContratoSelect && e.tipoContrato) {
        tipoContratoSelect.value = e.tipoContrato;
    }

    // Usuario (campo oculto)
    const usuarioHidden = document.querySelector("input[name='usuario.id']");
    if (usuarioHidden && e.usuarioId) {
        usuarioHidden.value = e.usuarioId;
    }
}

    // Ocultar sugerencias si hacés clic fuera
    document.addEventListener("click", function (event) {
        if (!sugerencias.contains(event.target) && event.target !== inputEmpleado) {
            sugerencias.innerHTML = "";
            sugerencias.style.display = "none";
        }
    });

    // Ocultar sugerencias al presionar ESC
    inputEmpleado.addEventListener("keydown", function (event) {
        if (event.key === "Escape") {
            sugerencias.innerHTML = "";
            sugerencias.style.display = "none";
        }
    });
});

// Modificación específica para el formulario de agregar empleado a semana
// Esta función complementa el código existente sin reemplazarlo

// Para el formulario de agregar empleado, necesitamos que al hacer click en una sugerencia
// se complete el input visible y el campo oculto con el DNI
document.addEventListener('DOMContentLoaded', function() {
    // Verificamos si estamos en la página de control de planilla
    const formAgregarEmpleado = document.querySelector('form[action*="/semana-empleado/agregar"]');

    if (formAgregarEmpleado) {
        const inputEmpleado = document.getElementById('empleado');
        const inputDni = document.getElementById('dniBusqueda');
        const sugerencias = document.getElementById('sugerencias');

        // Sobrescribimos el evento de click para las sugerencias específicamente para este formulario
        if (sugerencias) {
            sugerencias.addEventListener('click', function(e) {
                const sugerencia = e.target.closest('.list-group-item');
                if (sugerencia) {
                    e.preventDefault();
                    inputEmpleado.value = sugerencia.textContent.split(' - ')[0]; // Solo nombre y apellido
                    if (inputDni) {
                        inputDni.value = sugerencia.dataset.dni;
                    }
                    sugerencias.innerHTML = '';
                    sugerencias.style.display = 'none';
                }
            });
        }
    }
});