package com.epq.epqbackend.util;

import java.util.Set;

/**
 * Municipios válidos del sistema EPQ (Quindío).
 * Se usa para validar que los datos cargados pertenezcan a municipios reconocidos
 * y para normalizar los nombres al momento del procesamiento.
 */
public final class MunicipioConstantes {

    private MunicipioConstantes() {}

    /** Conjunto de municipios en mayúsculas para comparación exacta rápida */
    public static final Set<String> MUNICIPIOS_VALIDOS = Set.of(
            "BUENAVISTA",
            "CIRCASIA",
            "FILANDIA",
            "GENOVA",
            "LA TEBAIDA",
            "MONTENEGRO",
            "PIJAO",
            "QUIMBAYA",
            "SALENTO"
    );

    /**
     * Normaliza y valida un nombre de municipio.
     * Elimina tildes, convierte a mayúsculas y verifica que esté en la lista.
     *
     * @param nombre nombre del municipio tal como viene del archivo
     * @return nombre normalizado en mayúsculas si es válido, null si no se reconoce
     */
    public static String normalizar(String nombre) {
        if (nombre == null || nombre.trim().isEmpty()) return null;
        String normalizado = eliminarTildes(nombre.trim().toUpperCase());
        return MUNICIPIOS_VALIDOS.contains(normalizado) ? normalizado : null;
    }

    /**
     * Verifica si un nombre de municipio (ya normalizado) es válido.
     */
    public static boolean esValido(String nombre) {
        return nombre != null && MUNICIPIOS_VALIDOS.contains(eliminarTildes(nombre.trim().toUpperCase()));
    }

    /**
     * Elimina tildes y caracteres especiales del español para comparación robusta.
     */
    public static String eliminarTildes(String texto) {
        if (texto == null) return null;
        return texto
                .replace("Á", "A").replace("É", "E").replace("Í", "I")
                .replace("Ó", "O").replace("Ú", "U").replace("Ü", "U")
                .replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ü", "u")
                .replace("Ñ", "N").replace("ñ", "n");
    }
}
