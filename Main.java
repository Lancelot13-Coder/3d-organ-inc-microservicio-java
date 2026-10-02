package com.organinc.microservicio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Main {

    static String dbUrl;
    static String dbUser;
    static String dbPassword;

    public static void main(String[] args) throws IOException {
        String host = System.getenv("DB_HOST");
        String puertoDb = System.getenv().getOrDefault("DB_PORT", "5432");
        String nombreDb = System.getenv().getOrDefault("DB_NAME", "postgres");
        dbUser = System.getenv("DB_USER");
        dbPassword = System.getenv("DB_PASSWORD");
        dbUrl = "jdbc:postgresql://" + host + ":" + puertoDb + "/" + nombreDb;

        int puerto = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));

        HttpServer servidor = HttpServer.create(new InetSocketAddress(puerto), 0);
        servidor.createContext("/", new RaizHandler());
        servidor.createContext("/docs", new Docs.UiHandler());
        servidor.createContext("/openapi.json", new Docs.OpenApiHandler());
        servidor.createContext("/api/modelos", new ModelosHandler());
        servidor.setExecutor(null);
        servidor.start();
        System.out.println("Microservicio Java escuchando en el puerto " + puerto);
    }

    static Connection obtenerConexion() throws SQLException {
        return DriverManager.getConnection(dbUrl, dbUser, dbPassword);
    }

    static void agregarCors(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "POST, PUT, DELETE, OPTIONS");
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    }

    static void enviarJson(HttpExchange exchange, int codigo, Map<String, Object> cuerpo) throws IOException {
        agregarCors(exchange);
        byte[] bytes = JsonMinimo.aJson(cuerpo).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static void enviarError(HttpExchange exchange, int codigo, String detalle) throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("detail", detalle);
        enviarJson(exchange, codigo, cuerpo);
    }

    static class RaizHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            Map<String, Object> cuerpo = new LinkedHashMap<>();
            cuerpo.put("servicio", "microservicio-modelos-3d-java");
            cuerpo.put("estado", "ok");
            enviarJson(exchange, 200, cuerpo);
        }
    }

    static class ModelosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String metodo = exchange.getRequestMethod();
            String ruta = exchange.getRequestURI().getPath();

            if (metodo.equals("OPTIONS")) {
                agregarCors(exchange);
                exchange.sendResponseHeaders(200, -1);
                return;
            }

            if (metodo.equals("POST") && ruta.equals("/api/modelos")) {
                crear(exchange);
                return;
            }

            Integer id = extraerId(ruta);
            if (metodo.equals("PUT") && id != null) {
                editar(exchange, id);
                return;
            }
            if (metodo.equals("DELETE") && id != null) {
                eliminar(exchange, id);
                return;
            }

            enviarError(exchange, 405, "Método no permitido o ruta inválida");
        }

        private Integer extraerId(String ruta) {
            String prefijo = "/api/modelos/";
            if (!ruta.startsWith(prefijo)) {
                return null;
            }
            try {
                return Integer.parseInt(ruta.substring(prefijo.length()));
            } catch (NumberFormatException e) {
                return null;
            }
        }

        private void crear(HttpExchange exchange) throws IOException {
            Map<String, Object> datos;
            try {
                datos = JsonMinimo.parseObjeto(leerCuerpo(exchange.getRequestBody()));
            } catch (Exception e) {
                enviarError(exchange, 400, "JSON inválido en el cuerpo de la petición");
                return;
            }

            Object nombre = datos.get("nombre");
            Object categoria = datos.get("categoria");
            if (nombre == null || categoria == null) {
                enviarError(exchange, 422, "Los campos 'nombre' y 'categoria' son obligatorios");
                return;
            }
            String descripcion = datos.get("descripcion") != null ? datos.get("descripcion").toString() : "";
            boolean activo = datos.get("activo") == null || Boolean.TRUE.equals(datos.get("activo"));

            String sql = "INSERT INTO modelos (nombre, descripcion, categoria, activo) "
                    + "VALUES (?, ?, ?, ?) RETURNING id, fecha_registro";

            try (Connection conexion = Main.obtenerConexion();
                 PreparedStatement ps = conexion.prepareStatement(sql)) {
                ps.setString(1, nombre.toString());
                ps.setString(2, descripcion);
                ps.setString(3, categoria.toString());
                ps.setBoolean(4, activo);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Map<String, Object> resultado = new LinkedHashMap<>();
                        resultado.put("id", rs.getInt("id"));
                        resultado.put("nombre", nombre.toString());
                        resultado.put("descripcion", descripcion);
                        resultado.put("categoria", categoria.toString());
                        resultado.put("fecha_registro", rs.getTimestamp("fecha_registro").toInstant().toString());
                        resultado.put("activo", activo);
                        enviarJson(exchange, 201, resultado);
                    } else {
                        enviarError(exchange, 500, "No se pudo crear el registro");
                    }
                }
            } catch (SQLException e) {
                enviarError(exchange, 500, "Error de base de datos: " + e.getMessage());
            }
        }

        private void editar(HttpExchange exchange, int id) throws IOException {
            String cuerpoTexto = leerCuerpo(exchange.getRequestBody());
            Map<String, Object> cambios;
            try {
                cambios = JsonMinimo.parseObjeto(cuerpoTexto);
            } catch (Exception e) {
                enviarError(exchange, 400, "JSON inválido en el cuerpo de la petición");
                return;
            }

            List<String> asignaciones = new ArrayList<>();
            List<Object> valores = new ArrayList<>();
            for (String campo : new String[] {"nombre", "descripcion", "categoria", "activo"}) {
                if (cambios.containsKey(campo)) {
                    asignaciones.add(campo + " = ?");
                    valores.add(cambios.get(campo));
                }
            }

            if (asignaciones.isEmpty()) {
                enviarError(exchange, 400, "No se envió ningún campo para actualizar");
                return;
            }

            String sql = "UPDATE modelos SET " + String.join(", ", asignaciones) + " WHERE id = ?";

            try (Connection conexion = Main.obtenerConexion();
                 PreparedStatement ps = conexion.prepareStatement(sql)) {

                int i = 1;
                for (Object valor : valores) {
                    if (valor instanceof Boolean) {
                        ps.setBoolean(i, (Boolean) valor);
                    } else if (valor == null) {
                        ps.setNull(i, java.sql.Types.VARCHAR);
                    } else {
                        ps.setString(i, valor.toString());
                    }
                    i++;
                }
                ps.setInt(i, id);

                int filasAfectadas = ps.executeUpdate();
                if (filasAfectadas == 0) {
                    enviarError(exchange, 404, "Modelo 3D no encontrado");
                    return;
                }
            } catch (SQLException e) {
                enviarError(exchange, 500, "Error de base de datos: " + e.getMessage());
                return;
            }

            try (Connection conexion = Main.obtenerConexion();
                 PreparedStatement ps = conexion.prepareStatement(
                         "SELECT id, nombre, descripcion, categoria, fecha_registro, activo FROM modelos WHERE id = ?")) {
                ps.setInt(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        enviarJson(exchange, 200, filaAMapa(rs));
                    } else {
                        enviarError(exchange, 404, "Modelo 3D no encontrado");
                    }
                }
            } catch (SQLException e) {
                enviarError(exchange, 500, "Error de base de datos: " + e.getMessage());
            }
        }

        private void eliminar(HttpExchange exchange, int id) throws IOException {
            String sql = "DELETE FROM modelos WHERE id = ?";
            try (Connection conexion = Main.obtenerConexion();
                 PreparedStatement ps = conexion.prepareStatement(sql)) {
                ps.setInt(1, id);
                int filasAfectadas = ps.executeUpdate();
                if (filasAfectadas == 0) {
                    enviarError(exchange, 404, "Modelo 3D no encontrado");
                    return;
                }
                agregarCors(exchange);
                exchange.sendResponseHeaders(204, -1);
            } catch (SQLException e) {
                enviarError(exchange, 500, "Error de base de datos: " + e.getMessage());
            }
        }

        private Map<String, Object> filaAMapa(ResultSet rs) throws SQLException {
            Map<String, Object> resultado = new LinkedHashMap<>();
            resultado.put("id", rs.getInt("id"));
            resultado.put("nombre", rs.getString("nombre"));
            resultado.put("descripcion", rs.getString("descripcion"));
            resultado.put("categoria", rs.getString("categoria"));
            resultado.put("fecha_registro", rs.getTimestamp("fecha_registro").toInstant().toString());
            resultado.put("activo", rs.getBoolean("activo"));
            return resultado;
        }
    }

    static String leerCuerpo(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] datos = new byte[1024];
        int leidos;
        while ((leidos = is.read(datos)) != -1) {
            buffer.write(datos, 0, leidos);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}