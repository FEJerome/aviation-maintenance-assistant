package cn.pandazi.aviation_maintenance_assistant.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class FakeOpenAiServer implements AutoCloseable {

    private final HttpServer server;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<JsonNode> lastRequest = new AtomicReference<>();
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuthorization = new AtomicReference<>();
    private volatile int status = 200;
    private volatile boolean streaming;

    public FakeOpenAiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet();
            lastPath.set(exchange.getRequestURI().getPath());
            lastAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            JsonNode request = objectMapper.readTree(exchange.getRequestBody());
            lastRequest.set(request);
            streaming = request.path("stream").asBoolean(false);

            if (status != 200) {
                byte[] body = "{\"error\":{\"message\":\"synthetic provider detail\"}}"
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
                return;
            }

            if (streaming) {
                String body = "data: {\"id\":\"synthetic\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"Synthetic \"}}]}\n\n"
                        + "data: {\"id\":\"synthetic\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"stream\"}}]}\n\n"
                        + "data: [DONE]\n\n";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
                exchange.sendResponseHeaders(200, bytes.length);
                exchange.getResponseBody().write(bytes);
            } else {
                byte[] body = ("{\"id\":\"synthetic\",\"object\":\"chat.completion\","
                        + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                        + "\"content\":\"Synthetic answer\"},\"finish_reason\":\"stop\"}]}")
                        .getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    public int calls() {
        return calls.get();
    }

    public JsonNode lastRequest() {
        return lastRequest.get();
    }

    public String lastPath() {
        return lastPath.get();
    }

    public String lastAuthorization() {
        return lastAuthorization.get();
    }

    public void setStatus(int status) {
        this.status = status;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
