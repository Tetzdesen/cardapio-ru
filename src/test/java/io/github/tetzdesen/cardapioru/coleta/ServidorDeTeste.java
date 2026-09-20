package io.github.tetzdesen.cardapioru.coleta;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/** Servidor HTTP minimo para exercitar o retry sem tocar a rede de verdade. */
final class ServidorDeTeste implements AutoCloseable {

    private final HttpServer servidor;
    private final AtomicInteger chamadas = new AtomicInteger();
    private final List<String> userAgents = new ArrayList<>();

    /** {@code resposta} recebe o numero da chamada (1-based) e devolve o status. */
    ServidorDeTeste(IntFunction<Integer> resposta, String corpo) throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", (HttpExchange troca) -> {
            int n = chamadas.incrementAndGet();
            synchronized (userAgents) {
                userAgents.add(troca.getRequestHeaders().getFirst("User-Agent"));
            }
            int status = resposta.apply(n);
            byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
            troca.sendResponseHeaders(status, bytes.length);
            troca.getResponseBody().write(bytes);
            troca.close();
        });
        servidor.start();
    }

    String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/cardapio";
    }

    int chamadas() {
        return chamadas.get();
    }

    List<String> userAgents() {
        synchronized (userAgents) {
            return List.copyOf(userAgents);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
