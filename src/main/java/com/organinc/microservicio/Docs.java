package com.organinc.microservicio;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Documentación Swagger (OpenAPI 3): /docs (interfaz) y /openapi.json (especificación). */
class Docs {

    static final String OPENAPI = """
{
  "openapi": "3.0.3",
  "info": {
    "title": "Microservicio Java - 3D Organ Inc",
    "version": "1.0.0",
    "description": "Microservicio CRUD de modelos 3D de 3D-Organ-Inc. Escribe en la tabla 'modelos' de Supabase (PostgreSQL). Nota: este servicio expone crear, editar y eliminar."
  },
  "servers": [{ "url": "/" }],
  "tags": [
    { "name": "Estado", "description": "Verificar que el servicio está vivo" },
    { "name": "Modelos", "description": "Crear, editar y eliminar modelos 3D" }
  ],
  "paths": {
    "/": {
      "get": {
        "tags": ["Estado"],
        "summary": "Verifica que el servicio está vivo",
        "responses": {
          "200": {
            "description": "Servicio activo",
            "content": { "application/json": { "example": { "servicio": "microservicio-modelos-3d-java", "estado": "ok" } } }
          }
        }
      }
    },
    "/api/modelos": {
      "post": {
        "tags": ["Modelos"],
        "summary": "Crear un modelo 3D",
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "$ref": "#/components/schemas/ModeloEntrada" } } }
        },
        "responses": {
          "201": { "description": "Modelo creado", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Modelo" } } } },
          "400": { "description": "JSON inválido", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Error" } } } },
          "422": { "description": "Faltan 'nombre' o 'categoria'", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Error" } } } },
          "500": { "description": "Error de base de datos", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Error" } } } }
        }
      }
    },
    "/api/modelos/{id}": {
      "put": {
        "tags": ["Modelos"],
        "summary": "Editar un modelo 3D",
        "description": "Solo se actualizan los campos enviados (nombre, descripcion, categoria, activo). Debe enviarse al menos uno.",
        "parameters": [{ "$ref": "#/components/parameters/IdModelo" }],
        "requestBody": {
          "required": true,
          "content": { "application/json": { "schema": { "$ref": "#/components/schemas/ModeloCambios" } } }
        },
        "responses": {
          "200": { "description": "Modelo actualizado", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Modelo" } } } },
          "400": { "description": "Id no numérico, JSON inválido o sin campos", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Error" } } } },
          "404": { "description": "Modelo no encontrado", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Error" } } } },
          "500": { "description": "Error de base de datos", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Error" } } } }
        }
      },
      "delete": {
        "tags": ["Modelos"],
        "summary": "Eliminar un modelo 3D",
        "parameters": [{ "$ref": "#/components/parameters/IdModelo" }],
        "responses": {
          "204": { "description": "Eliminado (sin contenido)" },
          "400": { "description": "Id no numérico", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Error" } } } },
          "404": { "description": "Modelo no encontrado", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Error" } } } },
          "500": { "description": "Error de base de datos", "content": { "application/json": { "schema": { "$ref": "#/components/schemas/Error" } } } }
        }
      }
    }
  },
  "components": {
    "parameters": {
      "IdModelo": {
        "name": "id", "in": "path", "required": true,
        "description": "Id numérico del modelo",
        "schema": { "type": "integer", "example": 1 }
      }
    },
    "schemas": {
      "ModeloEntrada": {
        "type": "object",
        "required": ["nombre", "categoria"],
        "properties": {
          "nombre": { "type": "string", "example": "Cóclea 3D" },
          "descripcion": { "type": "string", "example": "Modelo anatómico de la cóclea" },
          "categoria": { "type": "string", "example": "🦻 Modelos del Oído" },
          "activo": { "type": "boolean", "default": true }
        }
      },
      "ModeloCambios": {
        "type": "object",
        "properties": {
          "nombre": { "type": "string", "example": "Cóclea 3D v2" },
          "descripcion": { "type": "string" },
          "categoria": { "type": "string", "example": "🤖 Modelos de Prueba" },
          "activo": { "type": "boolean", "example": false }
        }
      },
      "Modelo": {
        "type": "object",
        "properties": {
          "id": { "type": "integer", "example": 1 },
          "nombre": { "type": "string" },
          "descripcion": { "type": "string" },
          "categoria": { "type": "string" },
          "fecha_registro": { "type": "string", "format": "date-time" },
          "activo": { "type": "boolean" }
        }
      },
      "Error": {
        "type": "object",
        "properties": { "detail": { "type": "string", "example": "Modelo 3D no encontrado" } }
      }
    }
  }
}

""";

    static final String HTML = """
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="utf-8">
<title>Documentación API</title>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui.css">
</head>
<body>
<div id="swagger-ui"></div>
<script src="https://cdn.jsdelivr.net/npm/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
<script>
window.onload = function () {
  SwaggerUIBundle({ url: "/openapi.json", dom_id: "#swagger-ui" });
};
</script>
</body>
</html>

""";

    static void enviar(HttpExchange exchange, String tipo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", tipo);
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    static class OpenApiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enviar(exchange, "application/json; charset=utf-8", OPENAPI);
        }
    }

    static class UiHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            enviar(exchange, "text/html; charset=utf-8", HTML);
        }
    }
}
