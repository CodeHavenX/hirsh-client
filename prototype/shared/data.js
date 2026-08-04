const PATIENTS = [
  { id: '#00142', name: 'Maria Gonzalez Huerta', dob: '14/03/1989', phone: '987-654-321', doctor: 'Dr. Patel', lastVisit: '12 Abr 2026', blood: 'O+', allergies: 'Penicilina', dni: '45678901', sex: 'F' },
  { id: '#00138', name: 'Eduardo Remon Huertas', dob: '17/07/1962', phone: '912-345-678', doctor: 'Dr. Reyes', lastVisit: '17 Jun 2026', blood: 'A+', allergies: 'Ninguna', dni: '09147875', sex: 'M' },
  { id: '#00135', name: 'Jesus Alberto Mendoza Aguilar', dob: '10/08/1990', phone: '955-123-456', doctor: 'Dr. Patel', lastVisit: '18 Jun 2026', blood: 'B+', allergies: 'Ninguna', dni: '70567572', sex: 'M' },
  { id: '#00131', name: 'Maria Santos Vasquez Davila', dob: '29/01/1943', phone: '998-765-432', doctor: 'Dr. Lin', lastVisit: '19 Jun 2026', blood: 'AB+', allergies: 'Sulfas', dni: '07024120', sex: 'F' },
  { id: '#00129', name: 'Karla Sofia Ricaldi Sedano', dob: '15/10/2012', phone: '998-984-134', doctor: 'Dr. Reyes', lastVisit: '20 May 2026', blood: '—', allergies: 'Ninguna', dni: '70083906', sex: 'F' },
  { id: '#00124', name: 'Olga Karen Santiesteban Bracamonte', dob: '12/06/1980', phone: '944-556-677', doctor: 'Dr. Patel', lastVisit: '12 Jun 2026', blood: 'O-', allergies: 'Ninguna', dni: '40734432', sex: 'F' },
];

// Age is derived from dob (DD/MM/YYYY) instead of stored, so it can never
// drift out of sync with an edited birth date.
function getAge(dob) {
  const [d, m, y] = dob.split('/').map(Number);
  const today = new Date();
  let age = today.getFullYear() - y;
  if (today.getMonth() + 1 < m || (today.getMonth() + 1 === m && today.getDate() < d)) age--;
  return age;
}

// 'YYYY-MM-DD' (native <input type="date"> value) <-> 'DD/MM/YYYY' (storage format).
function formatDobForStorage(iso) {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}
function parseDobToInput(dob) {
  const [d, m, y] = dob.split('/');
  return `${y}-${m}-${d}`;
}

// Shared field descriptor for patient demographics, used by both
// register.html (new patient) and patient-edit.html (existing patient) so
// the two forms can't drift apart. Mirrors the HC_SECTIONS/SECTION_FIELDS
// pattern used for Historia Clinica.
const PATIENT_FIELDS = [
  { key: 'name', label: 'Nombre completo', type: 'text', placeholder: 'Ej: Maria Gonzalez Huerta', required: true },
  { key: 'dni', label: 'DNI', type: 'text', placeholder: '8 digitos', maxlength: 8, required: true },
  { key: 'dob', label: 'Fecha de nacimiento', type: 'date', required: true },
  { key: 'phone', label: 'Telefono de contacto', type: 'text', placeholder: '987-654-321', required: true },
  { key: 'sex', label: 'Sexo', type: 'select', required: true,
    options: [{ value: 'M', label: 'Masculino' }, { value: 'F', label: 'Femenino' }] },
  { key: 'blood', label: 'Grupo sanguineo', type: 'select',
    options: ['O+', 'O-', 'A+', 'A-', 'B+', 'B-', 'AB+', 'AB-'] },
  { key: 'allergies', label: 'Alergias conocidas', type: 'textarea', placeholder: 'Ej: Penicilina, sulfas, mariscos', rows: 2 },
  { key: 'doctor', label: 'Medico asignado', type: 'select',
    options: ['Dr. Patel', 'Dr. Reyes', 'Dr. Lin'] },
];

function patientFieldId(key) { return 'pf-' + key; }

// Reads the pf-* inputs rendered by renderPatientFieldsForm() back into a
// plain object keyed by PATIENT_FIELDS[].key.
function readPatientFieldsForm() {
  const values = {};
  PATIENT_FIELDS.forEach(f => {
    const el = document.getElementById(patientFieldId(f.key));
    if (!el) return;
    values[f.key] = f.type === 'date' ? formatDobForStorage(el.value) : el.value;
  });
  return values;
}

// Historia Clinica is filled in section-by-section, often over several days.
// `implemented: false` sections are stubbed (no digital form yet) but still
// show up in the section nav so the document's full shape stays visible.
const HC_SECTIONS = [
  { key: 'filiacion', label: 'Filiacion', implemented: true },
  { key: 'motivoIngreso', label: 'Motivo de Ingreso', implemented: true },
  { key: 'enfermedadActual', label: 'Enfermedad Actual', implemented: true },
  { key: 'antecedentes', label: 'Antecedentes', implemented: false },
  { key: 'historiaPersonal', label: 'Historia Personal', implemented: false },
  { key: 'examenFisico', label: 'Examen Fisico', implemented: true },
  { key: 'inventarioSintomas', label: 'Inventario de Sintomas', implemented: false },
  { key: 'examenPsicopatologico', label: 'Examen Psicopatologico', implemented: false },
  { key: 'diagnostico', label: 'Diagnostico', implemented: true },
  { key: 'plan', label: 'Plan de Trabajo', implemented: true },
];

const MOTIVO_INGRESO_OPTIONS = [
  { key: 'riesgoSuicida', label: 'Riesgo suicida' },
  { key: 'riesgoHomicida', label: 'Riesgo homicida' },
  { key: 'heteroagresividad', label: 'Heteroagresividad' },
  { key: 'agitacionPsicomotriz', label: 'Agitacion psicomotriz' },
  { key: 'psicosis', label: 'Psicosis' },
  { key: 'adicciones', label: 'Adicciones' },
  { key: 'trastornoAfecto', label: 'Trastorno del afecto' },
  { key: 'tca', label: 'Trastorno de la conducta alimentaria' },
  { key: 'precisionDiagnostica', label: 'Precision diagnostica' },
  { key: 'precisionTerapeutica', label: 'Precision terapeutica' },
  { key: 'otros', label: 'Otros' },
];

// Field definitions for each implemented Historia Clinica section. Shared by
// the section editor (historia-clinica.html, which also writes through these
// keys) and the print sheet (print.html, read-only) so labels never drift.
const SECTION_FIELDS = {
  filiacion: [
    { key: 'edad', label: 'Edad', type: 'text' },
    { key: 'fechaNacimiento', label: 'Fecha de nacimiento', type: 'text' },
    { key: 'estadoCivil', label: 'Estado civil', type: 'select', options: ['Soltero(a)', 'Casado(a)', 'Conviviente', 'Viudo(a)', 'Divorciado(a)'] },
    { key: 'sexo', label: 'Sexo', type: 'select', options: ['Masculino', 'Femenino'] },
    { key: 'dni', label: 'DNI', type: 'text' },
    { key: 'gradoInstruccion', label: 'Grado de instruccion', type: 'text' },
    { key: 'ocupacion', label: 'Ocupacion', type: 'text' },
    { key: 'lugarNacimiento', label: 'Lugar de nacimiento', type: 'text' },
    { key: 'lugarProcedencia', label: 'Lugar de procedencia', type: 'text' },
    { key: 'familiarResponsable', label: 'Familiar responsable (nombre y telefono)', type: 'text' },
    { key: 'direccion', label: 'Direccion', type: 'text' },
    { key: 'servicioIngreso', label: 'Servicio de ingreso', type: 'text' },
  ],
  enfermedadActual: [
    { key: 'tiempoEnfermedad', label: 'Tiempo de enfermedad', type: 'text' },
    { key: 'formaInicio', label: 'Forma de inicio', type: 'select', options: ['Insidioso', 'Agudo', 'Subagudo'] },
    { key: 'curso', label: 'Curso', type: 'select', options: ['Progresivo', 'Estacionario', 'Remitente'] },
    { key: 'duracionEpisodio', label: 'Duracion del episodio actual', type: 'text' },
    { key: 'relato', label: 'Relato', type: 'textarea', rows: 5 },
  ],
  examenFisico: [
    { key: 'pa', label: 'PA (mmHg)', type: 'text' },
    { key: 'fc', label: 'FC (lpm)', type: 'text' },
    { key: 'fr', label: 'FR (rpm)', type: 'text' },
    { key: 'temp', label: 'T° (°C)', type: 'text' },
    { key: 'peso', label: 'Peso (kg)', type: 'text' },
    { key: 'talla', label: 'Talla (cm)', type: 'text' },
    { key: 'imc', label: 'IMC (kg/m2)', type: 'text' },
    { key: 'estadoGeneral', label: 'Estado general', type: 'textarea', rows: 2 },
    { key: 'examenRegional.cabezaCuello', label: 'Cabeza y cuello', type: 'textarea', rows: 2 },
    { key: 'examenRegional.toraxPulmones', label: 'Torax y pulmones', type: 'textarea', rows: 2 },
    { key: 'examenRegional.corazon', label: 'Corazon', type: 'textarea', rows: 2 },
    { key: 'examenRegional.abdomen', label: 'Abdomen', type: 'textarea', rows: 2 },
    { key: 'examenRegional.neurologico', label: 'Neurologico', type: 'textarea', rows: 2 },
  ],
  diagnostico: [
    { key: 'ejeI', label: 'Eje I — Trastornos Clinicos-Psiquiatricos', type: 'textarea', rows: 2 },
    { key: 'ejeII', label: 'Eje II — Desarrollo y Personalidad', type: 'textarea', rows: 2 },
    { key: 'ejeIII', label: 'Eje III — Enfermedades Fisicas', type: 'textarea', rows: 2 },
    { key: 'ejeIV', label: 'Eje IV — Estresores Psicosociales', type: 'textarea', rows: 2 },
    { key: 'ejeV', label: 'Eje V — Evaluacion Global del Funcionamiento (EEAG)', type: 'text' },
  ],
  plan: [
    { key: 'lugarHospitalizacion', label: 'Lugar de hospitalizacion', type: 'text' },
    { key: 'examenesSolicitados', label: 'Examenes solicitados', type: 'textarea', rows: 2 },
    { key: 'psicofarmacos', label: 'Via de psicofarmacos', type: 'text' },
    { key: 'evaluacionesSolicitadas', label: 'Evaluaciones solicitadas', type: 'textarea', rows: 2 },
  ],
};

function getPath(obj, path) {
  if (!obj) return undefined;
  return path.split('.').reduce((o, k) => (o == null ? undefined : o[k]), obj);
}

function emptyHistoriaClinica() {
  return {
    sections: {
      filiacion: { complete: false, data: null },
      motivoIngreso: { complete: false, data: null },
      enfermedadActual: { complete: false, data: null },
      antecedentes: { complete: false, data: null },
      historiaPersonal: { complete: false, data: null },
      examenFisico: { complete: false, data: null },
      inventarioSintomas: { complete: false, data: null },
      examenPsicopatologico: { complete: false, data: null },
      diagnostico: { complete: false, data: null },
      plan: { complete: false, data: null },
    },
  };
}

// historiaClinica has no stored "estado" — it's always derived from how many
// sections are complete, so the badge can never drift out of sync with the data.
function hcCompletionCount(historiaClinica) {
  const keys = HC_SECTIONS.map(s => s.key);
  const done = keys.filter(k => historiaClinica.sections[k] && historiaClinica.sections[k].complete).length;
  return { done, total: keys.length };
}

function hcStatusLabel(historiaClinica) {
  const { done, total } = hcCompletionCount(historiaClinica);
  if (done === 0) return 'Borrador';
  if (done === total) return 'Completa';
  return 'En progreso';
}

// Patient -> Hospitalizacion (a stay). Each Hospitalizacion carries exactly
// one Historia Clinica (built incrementally, section by section, often across
// several days) and any number of Evolucion checkpoints (at least one a day).
const HOSPITALIZATIONS = {
  '#00129': [ // Karla Sofia Ricaldi Sedano — Historia Clinica Psiquiatrica (UHSMA)
    {
      id: 'h_ricaldi_1',
      servicio: 'Psiquiatria (UHSMA)',
      cama: '01',
      medicoResponsable: 'Dr. Reyes',
      fechaIngreso: '20 May 2026', horaIngreso: '14:30',
      fechaAlta: null, horaAlta: null,
      motivoIngreso: 'Heteroagresividad, psicosis, adicciones',
      estado: 'Activa',
      historiaClinica: {
        sections: {
          filiacion: {
            complete: true,
            data: {
              edad: '13 anos', fechaNacimiento: '15/10/2012', estadoCivil: 'Soltera', sexo: 'Femenino',
              dni: '70083906', gradoInstruccion: 'Secundaria Incompleta (1ero de secundaria)', ocupacion: 'Estudiante',
              lugarNacimiento: 'Hospital Edgardo Rebagliati Martins', lugarProcedencia: 'Villa el Salvador',
              familiarResponsable: 'Madre: Sandra Sedano Montalvo · 998984134',
              direccion: 'Manzana I Lote 34 3era etapa urb Pachacamac, Villa el Salvador',
              servicioIngreso: 'Emergencia de Pediatria, luego UHSMA',
            },
          },
          motivoIngreso: {
            complete: true,
            data: {
              riesgoSuicida: false, riesgoHomicida: false, heteroagresividad: true, agitacionPsicomotriz: false,
              psicosis: true, adicciones: true, trastornoAfecto: false, tca: false,
              precisionDiagnostica: true, precisionTerapeutica: false, otros: false, otrosDetalle: '',
            },
          },
          enfermedadActual: {
            complete: true,
            data: {
              tiempoEnfermedad: '2 anos', formaInicio: 'Insidioso', curso: 'Progresivo', duracionEpisodio: '4 dias',
              relato: 'Episodio actual de heteroagresividad hacia los padres, con conducta psicotica emergente y antecedente de consumo de sustancias. Episodios previos descritos por la madre desde hace 2 anos, con empeoramiento progresivo en los ultimos 4 dias.',
            },
          },
          antecedentes: { complete: false, data: null },
          historiaPersonal: { complete: false, data: null },
          examenFisico: {
            complete: true,
            data: {
              pa: '110/70', fc: '88', fr: '18', temp: '36.6', peso: '48', talla: '158', imc: '19.2',
              estadoGeneral: 'Aparente regular estado general. Independiente, funcional con dificultades sociales.',
              examenRegional: {
                cabezaCuello: 'Normocefalo. Cuello cilindrico, sin adenopatias.',
                toraxPulmones: 'MV pasa bien por ACP. No ruidos agregados.',
                corazon: 'RCR BI. No soplos.',
                abdomen: 'Blando, depresible. RHA+. No doloroso.',
                neurologico: 'Despierta. OTEP. EG 15. No focalizacion. No signos meningeos.',
              },
            },
          },
          inventarioSintomas: { complete: false, data: null },
          examenPsicopatologico: { complete: false, data: null },
          diagnostico: {
            complete: true,
            data: {
              ejeI: 'Psicosis aguda (F29.X); D/C Esquizofrenia infantil (F20.0)',
              ejeII: 'D/C Trastorno del Espectro Autista (F84.0)',
              ejeIII: '—',
              ejeIV: 'Apoyo familiar inadecuado (Z63.2); Social (Z73.4)',
              ejeV: 'EEAG 78%',
            },
          },
          plan: {
            complete: true,
            data: {
              lugarHospitalizacion: 'UHSMA: Area de Damas',
              examenesSolicitados: 'Examenes de laboratorio basales',
              psicofarmacos: 'EV, VO, IM',
              evaluacionesSolicitadas: 'Evaluacion por psicologia',
            },
          },
        },
      },
      // El analisis de esta carpeta confirma que no se genera EVO ni IC en este flujo.
      evoluciones: [],
    },
  ],

  '#00135': [ // Jesus Alberto Mendoza Aguilar — Emergencia, Topico de Medicina
    {
      id: 'h_mendoza_1',
      servicio: 'Emergencia - Topico de Medicina',
      cama: '0',
      medicoResponsable: 'Dr. Hirsh',
      fechaIngreso: '15 Jun 2026', horaIngreso: '12:50',
      fechaAlta: null, horaAlta: null,
      motivoIngreso: 'Sintomas respiratorios',
      estado: 'Activa',
      historiaClinica: {
        sections: {
          ...emptyHistoriaClinica().sections,
          filiacion: {
            complete: true,
            data: {
              edad: '35 anos', fechaNacimiento: '10/08/1990', estadoCivil: 'Soltero(a)', sexo: 'Masculino',
              dni: '70567572', gradoInstruccion: 'Superior', ocupacion: 'Su casa',
              lugarNacimiento: 'Lurin', lugarProcedencia: 'San Bartolo, Lima',
              familiarResponsable: '—', direccion: 'Asent.H. San Jose, San Bartolo',
              servicioIngreso: 'Emergencia - Topico de Medicina',
            },
          },
        },
      },
      evoluciones: [
        {
          id: 'v5c', fecha: '18 Jun 2026', hora: '22:30', medico: 'Dr. Hirsh',
          vitals: { pa: '115/72', fc: '80', fr: '20', temp: '37.0', satO2: '95', fio2: '24' },
          diagnosticos: [
            { codigoCie10: 'B59', descripcion: 'Neumonia por Pneumocystis' },
            { codigoCie10: 'B24', descripcion: 'VIH Estadio SIDA' },
          ],
          subjective: 'Paciente refiere notable mejoria de disnea. Tolera destete progresivo de oxigeno.',
          objective: 'PA 115/72, FC 80, FR 20, SatO2 95% con FiO2 24%. MV mejor ventilado bilateral, crepitos en disminucion.',
          assessment: 'Buena respuesta a tratamiento antimicrobiano. Resultado de BK pendiente aun.',
          plan: 'Continuar TMP-SMX y corticoides en descenso. Reevaluar destete de O2. Mantener IC Neumologia activa.',
          rx: 'TMP-SMX 15mg/kg/dia EV c/8h\nPrednisona 40mg VO c/12h (en descenso)\nO2 por CBN 1L',
          pronostico: 'Favorable', evolucion: 'Favorable',
          examenes: [
            { tipo: 'Laboratorio', nombre: 'Gasometria arterial - pO2', resultado: '76', unidad: 'mmHg', referencia: '80 - 100', fecha: '18 Jun 2026' },
            { tipo: 'Laboratorio', nombre: 'BK en esputo (seriado)', resultado: 'Pendiente', unidad: '—', referencia: 'Negativo', fecha: '18 Jun 2026' },
          ],
          examenesObs: 'Mejoria gasometrica respecto al ingreso. BK de esputo aun pendiente de resultado final.',
        },
        {
          id: 'v5', fecha: '16 Jun 2026', hora: '23:06', medico: 'Dr. Hirsh',
          vitals: { pa: '120/70', fc: '75', fr: '23', temp: '37.6', satO2: '98', fio2: '28' },
          diagnosticos: [
            { codigoCie10: 'B59', descripcion: 'Neumonia por Pneumocystis' },
            { codigoCie10: 'B24', descripcion: 'VIH Estadio SIDA' },
          ],
          subjective: 'Paciente aun con apoyo oxigenatorio CBN 2L. Enfermeria niega intercurrencias.',
          objective: 'PA 120/70, FC 75, FR 23, SatO2 98% con FiO2 28%. MV pasa en ACP, crepitos difusos. EG 11/15.',
          assessment: 'Hemodinamicamente estable. Ventila con apoyo O2. TEM muestra lesion intersticial difusa.',
          plan: 'Espera resultados BK. IC Neumologia. Continuar soporte oxigenatorio y ATB.',
          rx: 'TMP-SMX 15mg/kg/dia EV c/8h\nPrednisona 40mg VO c/12h\nO2 por CBN a FiO2 28%',
          pronostico: 'Reservado', evolucion: 'Estacionaria',
          examenes: [
            { tipo: 'Imagenologia', nombre: 'TEM de torax', resultado: 'Patron intersticial difuso bilateral', unidad: '—', referencia: '—', fecha: '16 Jun 2026' },
            { tipo: 'Laboratorio', nombre: 'BK en esputo (seriado)', resultado: 'Pendiente', unidad: '—', referencia: 'Negativo', fecha: '16 Jun 2026' },
          ],
          examenesObs: 'TEM de torax con infiltrado intersticial difuso bilateral en "vidrio esmerilado", compatible con neumonia por Pneumocystis jirovecii en paciente VIH positivo conocido. Se mantiene en espera de resultado de BK seriado.',
        },
        {
          id: 'v5a', fecha: '15 Jun 2026', hora: '14:00', medico: 'Dr. Hirsh',
          vitals: { pa: '110/70', fc: '88', fr: '26', temp: '38.2', satO2: '89', fio2: '21' },
          diagnosticos: [
            { codigoCie10: 'B59', descripcion: 'Neumonia por Pneumocystis' },
            { codigoCie10: 'B24', descripcion: 'VIH Estadio SIDA' },
          ],
          subjective: 'Paciente refiere disnea progresiva y tos seca de 3 dias. Niega fiebre cuantificada en casa.',
          objective: 'PA 110/70, FC 88, FR 26, SatO2 89% FiO2 21%. Polipnea, tiraje subcostal leve. MV disminuido difusamente.',
          assessment: 'Insuficiencia respiratoria hipoxemica, sospecha PCP en paciente VIH positivo conocido.',
          plan: 'Iniciar TMP-SMX EV, corticoides, O2 por CBN. Solicitar TEM torax y serologia. Aislamiento respiratorio.',
          rx: 'TMP-SMX 15mg/kg/dia EV c/8h\nPrednisona 40mg VO c/12h\nO2 por CBN 2L',
          pronostico: 'Reservado', evolucion: 'Desfavorable',
          examenes: [
            { tipo: 'Laboratorio', nombre: 'Hemograma - Hemoglobina', resultado: '13.8', unidad: 'g/dL', referencia: '13.0 - 17.0', fecha: '15 Jun 2026' },
            { tipo: 'Laboratorio', nombre: 'Gasometria arterial - pO2', resultado: '58', unidad: 'mmHg', referencia: '80 - 100', fecha: '15 Jun 2026' },
            { tipo: 'Laboratorio', nombre: 'Gasometria arterial - SatO2', resultado: '89', unidad: '%', referencia: '95 - 100', fecha: '15 Jun 2026' },
          ],
          examenesObs: 'Gasometria arterial compatible con insuficiencia respiratoria hipoxemica. Se solicita TEM de torax y serologia VIH confirmatoria.',
        },
      ],
    },
  ],

  '#00138': [ // Eduardo Remon Huertas — Emergencia, Topico de Medicina
    {
      id: 'h_remon_1',
      servicio: 'Emergencia - Topico de Medicina',
      cama: '0',
      medicoResponsable: 'Dr. Hirsh',
      fechaIngreso: '14 Jun 2026', horaIngreso: '09:15',
      fechaAlta: null, horaAlta: null,
      motivoIngreso: 'Hematemesis',
      estado: 'Activa',
      historiaClinica: emptyHistoriaClinica(),
      evoluciones: [
        {
          id: 'v4b', fecha: '17 Jun 2026', hora: '09:00', medico: 'Dr. Hirsh',
          vitals: { pa: '125/75', fc: '78', fr: '18', temp: '36.6', satO2: '97', fio2: '21' },
          diagnosticos: [
            { codigoCie10: 'K92.0', descripcion: 'Hemorragia digestiva alta' },
          ],
          subjective: 'Tolera dieta liquida. Sin nuevos episodios de hematemesis ni melena.',
          objective: 'PA 125/75, FC 78. ABD blando, no doloroso. Sin palidez activa.',
          assessment: 'VEDA evidencio ulcera duodenal Forrest III, ya tratada endoscopicamente. HB estable en 10.5.',
          plan: 'Alta probable en las proximas 24h con omeprazol VO y control ambulatorio por gastroenterologia.',
          rx: 'Omeprazol 40mg VO c/24h',
          pronostico: 'Favorable', evolucion: 'Favorable',
          examenes: [
            { tipo: 'Otro', nombre: 'VEDA (endoscopia digestiva alta)', resultado: 'Ulcera duodenal Forrest III', unidad: '—', referencia: '—', fecha: '17 Jun 2026' },
            { tipo: 'Laboratorio', nombre: 'Hemograma - Hemoglobina', resultado: '10.5', unidad: 'g/dL', referencia: '13.0 - 17.0', fecha: '17 Jun 2026' },
          ],
          examenesObs: 'VEDA evidencio ulcera duodenal Forrest III, tratada endoscopicamente sin complicaciones. Hemoglobina estable en control.',
        },
        {
          id: 'v4', fecha: '16 Jun 2026', hora: '08:40', medico: 'Dr. Hirsh',
          vitals: { pa: '140/70', fc: '91', fr: '20', temp: '36.8', satO2: '96', fio2: '21' },
          diagnosticos: [
            { codigoCie10: 'K92.0', descripcion: 'Hemorragia digestiva alta' },
          ],
          subjective: 'Dolor abdominal en disminucion. Niega SAT.',
          objective: 'PA 140/70, FC 91. Palidez +/+++. ABD globuloso, leve dolor epigastrio. EG 15/15.',
          assessment: 'Paciente hemodinamicamente estable. HB 9.9 macrocitica hipercromica.',
          plan: 'IC Gastroenterologia para VEDA. Continuar indicaciones medicas.',
          rx: 'Omeprazol 40mg EV c/12h\nNaCl 0.9% 1000cc EV',
          pronostico: 'Favorable', evolucion: 'Favorable',
          examenes: [
            { tipo: 'Laboratorio', nombre: 'Hemograma - Hemoglobina', resultado: '9.9', unidad: 'g/dL', referencia: '13.0 - 17.0', fecha: '16 Jun 2026' },
            { tipo: 'Laboratorio', nombre: 'Hemograma - VCM', resultado: '102', unidad: 'fL', referencia: '80 - 100', fecha: '16 Jun 2026' },
          ],
          examenesObs: 'Anemia macrocitica hipercromica en mejoria respecto al ingreso. Se mantiene IC a Gastroenterologia para VEDA.',
        },
        {
          id: 'v4a', fecha: '14 Jun 2026', hora: '11:00', medico: 'Dr. Hirsh',
          vitals: { pa: '100/60', fc: '105', fr: '22', temp: '36.5', satO2: '95', fio2: '21' },
          diagnosticos: [
            { codigoCie10: 'K92.0', descripcion: 'Hemorragia digestiva alta' },
          ],
          subjective: 'Paciente con hematemesis previa al ingreso, aprox 500ml en domicilio. Refiere mareos.',
          objective: 'PA 100/60, FC 105, palidez +++. ABD blando, doloroso epigastrio leve.',
          assessment: 'HDA activa, hemodinamicamente comprometido al ingreso. Requiere reanimacion con fluidos.',
          plan: 'NaCl 0.9% EV, omeprazol EV en bolo, solicitar HB seriada y IC Gastroenterologia urgente.',
          rx: 'Omeprazol 80mg EV bolo, luego infusion\nNaCl 0.9% 1000cc EV a chorro',
          pronostico: 'Reservado', evolucion: 'Estacionaria',
          examenes: [
            { tipo: 'Laboratorio', nombre: 'Hemograma - Hemoglobina', resultado: '8.2', unidad: 'g/dL', referencia: '13.0 - 17.0', fecha: '14 Jun 2026' },
            { tipo: 'Laboratorio', nombre: 'Grupo sanguineo y factor Rh', resultado: 'O+', unidad: '—', referencia: '—', fecha: '14 Jun 2026' },
          ],
          examenesObs: 'Hemoglobina baja al ingreso, compatible con hemorragia digestiva activa. Se solicita HB seriada y se reserva paquete globular.',
        },
      ],
    },
  ],

  '#00131': [ // Maria Santos Vasquez Davila — Emergencia, Topico de Medicina
    {
      id: 'h_vasquez_1',
      servicio: 'Emergencia - Topico de Medicina',
      cama: '0',
      medicoResponsable: 'Dr. Hirsh',
      fechaIngreso: '15 Jun 2026', horaIngreso: '19:20',
      fechaAlta: null, horaAlta: null,
      motivoIngreso: 'Insuficiencia respiratoria',
      estado: 'Activa',
      historiaClinica: emptyHistoriaClinica(),
      evoluciones: [
        {
          id: 'v6b', fecha: '19 Jun 2026', hora: '08:10', medico: 'Dr. Hirsh',
          vitals: { pa: '145/62', fc: '76', fr: '24', temp: '36.8', satO2: '91', fio2: '45' },
          diagnosticos: [
            { codigoCie10: 'J96.0', descripcion: 'Insuficiencia respiratoria aguda tipo 1' },
            { codigoCie10: '—', descripcion: 'D/C Neoplasia pulmonar vs TBC' },
          ],
          subjective: 'Paciente persiste con requerimiento oxigenatorio, refiere leve mejoria de disnea en reposo.',
          objective: 'PA 145/62, FC 76, FR 24, SatO2 91% con FiO2 45%. MV persiste disminuido AHT, sin progresion de tirajes.',
          assessment: 'Cuadro respiratorio en meseta. BFC programada por Neumologia pendiente de resultados.',
          plan: 'Mantener soporte O2, esperar BFC y cultivo de esputo. Reevaluar diariamente.',
          rx: 'Ceftriaxona 2g EV c/24h\nAzitromicina 500mg EV c/24h\nO2 por MV a FiO2 45%',
          pronostico: 'Reservado', evolucion: 'Estacionaria',
          examenes: [
            { tipo: 'Otro', nombre: 'Broncofibroscopia (BFC)', resultado: 'Pendiente', unidad: '—', referencia: '—', fecha: '19 Jun 2026' },
          ],
          examenesObs: 'Muestra obtenida por broncofibroscopia enviada a anatomia patologica y cultivo BK; resultado pendiente.',
        },
        {
          id: 'v6', fecha: '17 Jun 2026', hora: '07:50', medico: 'Dr. Hirsh',
          vitals: { pa: '150/60', fc: '73', fr: '23', temp: '36.9', satO2: '92', fio2: '50' },
          diagnosticos: [
            { codigoCie10: 'J96.0', descripcion: 'Insuficiencia respiratoria aguda tipo 1' },
            { codigoCie10: '—', descripcion: 'D/C Neoplasia pulmonar vs TBC' },
          ],
          subjective: 'Paciente refiere mejoria y menor falta de aire. Comoda con MV a 15L.',
          objective: 'PA 150/60, FC 73, FR 23, SatO2 92% con FiO2 50%. MV disminuido AHT, uso musculatura accesoria. EG 14/15.',
          assessment: 'Hemodinamicamente estable. TEM con multiples lesiones nodulares irregulares bilaterales. BK negativo x2.',
          plan: 'BFC por Neumologia. Espera cultivo esputo. Continuar ATB y soporte O2.',
          rx: 'Ceftriaxona 2g EV c/24h\nAzitromicina 500mg EV c/24h\nO2 por MV a FiO2 50%',
          pronostico: 'Reservado', evolucion: 'Estacionaria',
          examenes: [
            { tipo: 'Imagenologia', nombre: 'TEM de torax', resultado: 'Multiples lesiones nodulares irregulares bilaterales', unidad: '—', referencia: '—', fecha: '17 Jun 2026' },
            { tipo: 'Laboratorio', nombre: 'BK en esputo (1ra muestra)', resultado: 'Negativo', unidad: '—', referencia: 'Negativo', fecha: '17 Jun 2026' },
            { tipo: 'Laboratorio', nombre: 'BK en esputo (2da muestra)', resultado: 'Negativo', unidad: '—', referencia: 'Negativo', fecha: '17 Jun 2026' },
          ],
          examenesObs: 'TEM de torax con multiples lesiones nodulares irregulares bilaterales, sugerentes de proceso neoplasico vs TBC miliar. BK seriado negativo en 2 muestras. Se programa broncofibroscopia por Neumologia.',
        },
        {
          id: 'v6a', fecha: '15 Jun 2026', hora: '21:00', medico: 'Dr. Hirsh',
          vitals: { pa: '160/65', fc: '80', fr: '26', temp: '37.0', satO2: '85', fio2: '21' },
          diagnosticos: [
            { codigoCie10: 'J96.0', descripcion: 'Insuficiencia respiratoria aguda tipo 1' },
          ],
          subjective: 'Paciente refiere disnea progresiva de 1 semana, tos seca, sin fiebre.',
          objective: 'PA 160/65, FC 80, FR 26, SatO2 85% FiO2 21%. Uso de musculatura accesoria, MV disminuido difuso AHT.',
          assessment: 'Insuficiencia respiratoria aguda hipoxemica. Se inicia soporte y se solicitan estudios de imagen.',
          plan: 'O2 por mascara de Venturi a FiO2 35%, TEM torax, IC Neumologia.',
          rx: 'O2 por MV a FiO2 35%\nCeftriaxona 2g EV c/24h',
          pronostico: 'Reservado', evolucion: 'Estacionaria',
        },
      ],
    },
  ],

  '#00142': [ // Maria Gonzalez Huerta — 3 short, already-discharged stays
    {
      id: 'h_gonzalez_1',
      servicio: 'Medicina General',
      cama: '12',
      medicoResponsable: 'Dr. Patel',
      fechaIngreso: '10 Abr 2026', horaIngreso: '09:00',
      fechaAlta: '12 Abr 2026', horaAlta: '11:30',
      motivoIngreso: 'Tos productiva con fiebre',
      estado: 'Alta',
      historiaClinica: {
        sections: {
          ...emptyHistoriaClinica().sections,
          filiacion: { complete: true, data: {
            edad: '37 anos', fechaNacimiento: '14/03/1989', estadoCivil: 'Casada', sexo: 'Femenino',
            dni: '45678901', gradoInstruccion: 'Superior', ocupacion: 'Independiente',
            lugarNacimiento: 'Lima', lugarProcedencia: 'Lima', familiarResponsable: '—',
            direccion: '—', servicioIngreso: 'Medicina General',
          }},
          diagnostico: { complete: true, data: {
            ejeI: '—', ejeII: '—', ejeIII: 'Bronquitis aguda (J20.9)', ejeIV: '—', ejeV: '—',
          }},
          plan: { complete: true, data: {
            lugarHospitalizacion: 'Medicina General', examenesSolicitados: '—',
            psicofarmacos: '—', evaluacionesSolicitadas: '—',
          }},
        },
      },
      evoluciones: [
        {
          id: 'v1', fecha: '12 Abr 2026', hora: '10:15', medico: 'Dr. Patel',
          vitals: { pa: '118/76', fc: '78', fr: '18', temp: '37.8', satO2: '98', fio2: '21' },
          diagnosticos: [{ codigoCie10: 'J20.9', descripcion: 'Bronquitis aguda' }],
          subjective: 'Tos productiva por 5 dias, esputo amarillo-verdoso. Fiebre baja. Sin dolor toracico ni disnea en reposo.',
          objective: 'T 37.8C, FR 18, SpO2 98%. Crepitos dispersos base derecha; sin sibilancias. Garganta levemente inyectada.',
          assessment: 'Bronquitis aguda, probablemente bacteriana dado el esputo y hallazgos focales.',
          plan: 'Antibioticos + cuidados de soporte. Revision en 7 dias o antes si empeora.',
          rx: 'Amoxicilina 500mg — 1 capsula tres veces al dia — 7 dias\nParacetamol 500mg — PRN para fiebre, max 4x/dia',
          pronostico: 'Favorable', evolucion: 'Favorable',
        },
      ],
    },
    {
      id: 'h_gonzalez_2',
      servicio: 'Medicina General',
      cama: '08',
      medicoResponsable: 'Dr. Patel',
      fechaIngreso: '31 Ene 2026', horaIngreso: '08:30',
      fechaAlta: '02 Feb 2026', horaAlta: '10:00',
      motivoIngreso: 'Control de hipertension arterial',
      estado: 'Alta',
      historiaClinica: emptyHistoriaClinica(),
      evoluciones: [
        {
          id: 'v2', fecha: '02 Feb 2026', hora: '09:30', medico: 'Dr. Patel',
          vitals: { pa: '138/88', fc: '72', fr: '16', temp: '36.5', satO2: '99', fio2: '21' },
          diagnosticos: [{ codigoCie10: 'I10', descripcion: 'Hipertension arterial' }],
          subjective: 'Paciente refiere adherencia al tratamiento. Niega cefalea, mareos o vision borrosa.',
          objective: 'PA 138/88 mmHg, FC 72 lpm. Examen cardiovascular sin alteraciones.',
          assessment: 'HTA en control parcial. Buen cumplimiento terapeutico.',
          plan: 'Continuar Lisinopril 10mg OD. Control en 3 meses. Dieta hiposodica.',
          rx: 'Lisinopril 10mg — 1 tableta cada manana — continuo',
          pronostico: 'Favorable', evolucion: 'Favorable',
          examenes: [
            { tipo: 'Laboratorio', nombre: 'Creatinina serica', resultado: '0.8', unidad: 'mg/dL', referencia: '0.6 - 1.2', fecha: '02 Feb 2026' },
            { tipo: 'Laboratorio', nombre: 'Potasio serico', resultado: '4.3', unidad: 'mEq/L', referencia: '3.5 - 5.0', fecha: '02 Feb 2026' },
          ],
          examenesObs: 'Funcion renal y electrolitos dentro de limites normales en control de paciente con tratamiento antihipertensivo.',
        },
      ],
    },
    {
      id: 'h_gonzalez_3',
      servicio: 'Medicina General',
      cama: '08',
      medicoResponsable: 'Dr. Reyes',
      fechaIngreso: '14 Nov 2025', horaIngreso: '08:00',
      fechaAlta: '15 Nov 2025', horaAlta: '09:00',
      motivoIngreso: 'Examen fisico anual',
      estado: 'Alta',
      historiaClinica: emptyHistoriaClinica(),
      evoluciones: [
        {
          id: 'v3', fecha: '15 Nov 2025', hora: '08:45', medico: 'Dr. Reyes',
          vitals: { pa: '125/78', fc: '68', fr: '16', temp: '36.5', satO2: '99', fio2: '21' },
          diagnosticos: [{ codigoCie10: 'Z00.0', descripcion: 'Control de salud de rutina' }],
          subjective: 'Paciente acude a control anual. Sin quejas actuales.',
          objective: 'PA 125/78, FC 68, T 36.5. Examen fisico dentro de limites normales.',
          assessment: 'Paciente en buen estado general. Sin hallazgos patologicos.',
          plan: 'Solicitar hemograma, perfil lipidico, glucosa. Proximo control en 1 ano.',
          rx: '—',
          pronostico: 'Favorable', evolucion: 'Favorable',
          examenes: [
            { tipo: 'Laboratorio', nombre: 'Hemograma completo', resultado: 'Normal', unidad: '—', referencia: '—', fecha: '15 Nov 2025' },
            { tipo: 'Laboratorio', nombre: 'Perfil lipidico - Colesterol total', resultado: '188', unidad: 'mg/dL', referencia: '< 200', fecha: '15 Nov 2025' },
            { tipo: 'Laboratorio', nombre: 'Glucosa en ayunas', resultado: '92', unidad: 'mg/dL', referencia: '70 - 100', fecha: '15 Nov 2025' },
          ],
          examenesObs: 'Resultados de control anual dentro de parametros normales. Se recomienda mantener habitos saludables y control en 1 ano.',
        },
      ],
    },
  ],
};

const ACCOUNTS = [
  { name: 'Dr. Anita Patel', username: 'apatel', role: 'Medico', status: 'active', lastLogin: 'Hoy 08:12' },
  { name: 'Dr. Marco Reyes', username: 'mreyes', role: 'Medico', status: 'active', lastLogin: 'Ayer 17:40' },
  { name: 'Dr. Sara Lin', username: 'slin', role: 'Medico', status: 'active', lastLogin: '28 May 2026' },
  { name: 'Administrador', username: 'admin', role: 'Admin', status: 'active', lastLogin: 'Hoy 07:55' },
  { name: 'Dr. Tom Veer', username: 'tveer', role: 'Medico', status: 'off', lastLogin: '—' },
];

// State helpers using localStorage
const Store = {
  get(key) { try { return JSON.parse(localStorage.getItem('hiss_' + key)); } catch { return null; } },
  set(key, val) { localStorage.setItem('hiss_' + key, JSON.stringify(val)); },
  clear() { Object.keys(localStorage).filter(k => k.startsWith('hiss_')).forEach(k => localStorage.removeItem(k)); },
};

// Patient edits (and new patients registered) are persisted in localStorage,
// keyed by patient id, and re-applied onto the seed PATIENTS array on every
// page load -- this is what lets edits survive navigating to a different page.
function nextPatientId() {
  const max = Math.max(...PATIENTS.map(p => parseInt(p.id.replace('#', ''), 10)));
  return '#' + String(max + 1).padStart(5, '0');
}

function savePatientEdits(patientId, newValues) {
  const edits = Store.get('patientEdits') || {};
  edits[patientId] = { ...edits[patientId], ...newValues };
  Store.set('patientEdits', edits);
}

// fieldsChanged: [{ field, label, oldValue, newValue }] -- one log entry per
// save action, grouping every field changed in that save under one
// username/timestamp.
function logPatientChange(patientId, fieldsChanged) {
  if (!fieldsChanged.length) return;
  const user = Store.get('user') || {};
  const now = new Date();
  const entry = {
    changedBy: user.username || 'desconocido',
    fecha: now.toLocaleDateString('es-PE', { day: '2-digit', month: 'short', year: 'numeric' }),
    hora: now.toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' }),
    fields: fieldsChanged,
  };
  const log = Store.get('patientChangeLog') || {};
  log[patientId] = [entry, ...(log[patientId] || [])];
  Store.set('patientChangeLog', log);
}

// Static sample history, in the same shape logPatientChange() produces
// (newest first within each patient). Real edits made via patient-edit.html
// are tracked separately in Store and always shown ahead of these.
const PATIENT_CHANGE_LOG_SEED = {
  '#00142': [ // Maria Gonzalez Huerta
    {
      changedBy: 'apatel', fecha: '05 May 2026', hora: '11:20',
      fields: [{ field: 'allergies', label: 'Alergias conocidas', oldValue: 'Ninguna', newValue: 'Penicilina' }],
    },
    {
      changedBy: 'mreyes', fecha: '13 Abr 2026', hora: '08:30',
      fields: [{ field: 'phone', label: 'Telefono de contacto', oldValue: '987-654-320', newValue: '987-654-321' }],
    },
  ],
  '#00131': [ // Maria Santos Vasquez Davila
    {
      changedBy: 'admin', fecha: '16 Jun 2026', hora: '09:00',
      fields: [{ field: 'doctor', label: 'Medico asignado', oldValue: 'Dr. Reyes', newValue: 'Dr. Lin' }],
    },
    {
      changedBy: 'mreyes', fecha: '15 Jun 2026', hora: '20:00',
      fields: [{ field: 'blood', label: 'Grupo sanguineo', oldValue: '—', newValue: 'AB+' }],
    },
  ],
  '#00138': [ // Eduardo Remon Huertas
    {
      changedBy: 'slin', fecha: '14 Jun 2026', hora: '10:30',
      fields: [{ field: 'dni', label: 'DNI', oldValue: '09147785', newValue: '09147875' }],
    },
  ],
};

function getPatientChangeLog(patientId) {
  const stored = (Store.get('patientChangeLog') || {})[patientId] || [];
  return [...stored, ...(PATIENT_CHANGE_LOG_SEED[patientId] || [])];
}

(function hydratePatients() {
  const edits = Store.get('patientEdits') || {};
  PATIENTS.forEach(p => { if (edits[p.id]) Object.assign(p, edits[p.id]); });
  (Store.get('patientsCreated') || []).forEach(p => {
    if (!PATIENTS.some(existing => existing.id === p.id)) PATIENTS.push(p);
  });
})();

function showEl(id) { document.getElementById(id).classList.remove('hidden'); }
function hideEl(id) { document.getElementById(id).classList.add('hidden'); }
function toggleNav() { document.querySelector('.app').classList.toggle('nav-open'); }

// Cross-page hand-off travels through the URL (not just localStorage), since
// localStorage doesn't reliably survive a page-to-page navigation when these
// files are opened directly via file:// in every browser.
function getParam(name) {
  return new URLSearchParams(location.search).get(name);
}

function buildUrl(page, params) {
  const qs = new URLSearchParams();
  Object.entries(params).forEach(([k, v]) => { if (v != null) qs.set(k, v); });
  const str = qs.toString();
  return str ? `${page}?${str}` : page;
}

function getSelectedPatient() {
  const id = getParam('patient') || Store.get('selectedPatient');
  return PATIENTS.find(p => p.id === id) || null;
}

// A hospitalizacion is either one of the static historical stays (looked up
// by id under the patient) or a freshly-created one from admision.html that
// only exists in the URL.
function getSelectedHospitalization(patient) {
  const p = patient || getSelectedPatient();
  const hospId = getParam('hosp');
  if (hospId && p) {
    const match = (HOSPITALIZATIONS[p.id] || []).find(h => h.id === hospId);
    if (match) return match;
  }
  const hospData = getParam('hospData');
  if (hospData) { try { return JSON.parse(hospData); } catch { return null; } }
  return null;
}

function hospitalizacionLinkParams(patient, hosp) {
  const isStatic = (HOSPITALIZATIONS[patient.id] || []).some(h => h.id === hosp.id);
  return isStatic
    ? { patient: patient.id, hosp: hosp.id }
    : { patient: patient.id, hospData: JSON.stringify(hosp) };
}

// An evolucion is either one of the static entries inside a hospitalizacion
// (looked up by id) or a freshly-created one from evolucion.html. The parent
// hospitalizacion identity always travels alongside it (by id if static, by
// full payload if not) so the evolucion page can show hospitalizacion context
// and link back to it.
function getSelectedEvolucion(hosp) {
  const h = hosp || getSelectedHospitalization();
  const evoId = getParam('evo');
  if (evoId && h) {
    const match = (h.evoluciones || []).find(e => e.id === evoId);
    if (match) return match;
  }
  const evoData = getParam('evoData');
  if (evoData) { try { return JSON.parse(evoData); } catch { return null; } }
  return null;
}

function evolucionLinkParams(patient, hosp, evo) {
  const hospStatic = (HOSPITALIZATIONS[patient.id] || []).some(h => h.id === hosp.id);
  const evoStatic = (hosp.evoluciones || []).some(e => e.id === evo.id);
  return {
    patient: patient.id,
    ...(hospStatic ? { hosp: hosp.id } : { hospData: JSON.stringify(hosp) }),
    ...(evoStatic ? { evo: evo.id } : { evoData: JSON.stringify(evo) }),
  };
}

function getInitials(name) {
  return name.split(' ').map(n => n[0]).slice(0, 2).join('');
}
