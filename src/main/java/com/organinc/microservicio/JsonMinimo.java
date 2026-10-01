package com.organinc.microservicio;

import java.util.LinkedHashMap;
import java.util.Map;

class JsonMinimo {

    private final String texto;
    private int pos = 0;

    private JsonMinimo(String texto) {
        this.texto = texto;
    }

    static Map<String, Object> parseObjeto(String json) {
        return new JsonMinimo(json).leerObjeto();
    }

    private Map<String, Object> leerObjeto() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        saltarEspacios();
        esperar('{');
        saltarEspacios();
        if (mirar() == '}') {
            pos++;
            return mapa;
        }
        while (true) {
            saltarEspacios();
            String clave = leerString();
            saltarEspacios();
            esperar(':');
            saltarEspacios();
            Object valor = leerValor();
            mapa.put(clave, valor);
            saltarEspacios();
            char c = mirar();
            pos++;
            if (c == ',') {
                continue;
            }
            if (c == '}') {
                break;
            }
            throw new RuntimeException("JSON inválido cerca de la posición " + pos);
        }
        return mapa;
    }

    private Object leerValor() {
        char c = mirar();
        if (c == '"') {
            return leerString();
        }
        if (c == 't') {
            esperarLiteral("true");
            return Boolean.TRUE;
        }
        if (c == 'f') {
            esperarLiteral("false");
            return Boolean.FALSE;
        }
        if (c == 'n') {
            esperarLiteral("null");
            return null;
        }
        int inicio = pos;
        while (pos < texto.length()
                && (Character.isDigit(texto.charAt(pos)) || texto.charAt(pos) == '-' || texto.charAt(pos) == '.')) {
            pos++;
        }
        return Double.parseDouble(texto.substring(inicio, pos));
    }

    private String leerString() {
        esperar('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = texto.charAt(pos++);
            if (c == '"') {
                break;
            }
            if (c == '\\') {
                char siguiente = texto.charAt(pos++);
                switch (siguiente) {
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case '/': sb.append('/'); break;
                    default: sb.append(siguiente);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private char mirar() {
        saltarEspacios();
        return texto.charAt(pos);
    }

    private void esperar(char c) {
        if (texto.charAt(pos) != c) {
            throw new RuntimeException("Se esperaba '" + c + "' en la posición " + pos);
        }
        pos++;
    }

    private void esperarLiteral(String literal) {
        if (!texto.startsWith(literal, pos)) {
            throw new RuntimeException("Token inválido en la posición " + pos);
        }
        pos += literal.length();
    }

    private void saltarEspacios() {
        while (pos < texto.length() && Character.isWhitespace(texto.charAt(pos))) {
            pos++;
        }
    }

    static String aJson(Map<String, Object> mapa) {
        StringBuilder sb = new StringBuilder("{");
        boolean primero = true;
        for (Map.Entry<String, Object> entrada : mapa.entrySet()) {
            if (!primero) {
                sb.append(",");
            }
            primero = false;
            sb.append('"').append(escapar(entrada.getKey())).append("\":");
            Object valor = entrada.getValue();
            if (valor == null) {
                sb.append("null");
            } else if (valor instanceof Boolean || valor instanceof Integer
                    || valor instanceof Long || valor instanceof Double) {
                sb.append(valor.toString());
            } else {
                sb.append('"').append(escapar(valor.toString())).append('"');
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escapar(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}