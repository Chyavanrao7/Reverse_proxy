package proxy;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class ProxyHandler implements Runnable {

    private static final int BUFFER_SIZE = 8192;

    private static final int BACKEND_TIMEOUT_MS = 5000;

    private final Socket clientSocket;
    private final LoadBalancer loadBalancer;

    public ProxyHandler(Socket clientSocket, LoadBalancer loadBalancer) {
        this.clientSocket = clientSocket;
        this.loadBalancer = loadBalancer;
    }

    @Override
    public void run() {
        String clientAddr = clientSocket.getRemoteSocketAddress().toString();

        try (clientSocket) {
            byte[] requestBytes = readFromSocket(clientSocket);
            if (requestBytes.length == 0) {
                return;
            }

            String requestLine = extractFirstLine(requestBytes);

            Backend backend = loadBalancer.next();
            if (backend == null) {
                sendServiceUnavailable(clientSocket);
                System.err.printf("[%s] ERROR: no healthy backends available%n", Instant.now());
                return;
            }

            System.out.printf("[%s] %s → %s%n", Instant.now(), requestLine, backend);

            try (Socket backendSocket = new Socket(backend.getHost(), backend.getPort())) {
                backendSocket.setSoTimeout(BACKEND_TIMEOUT_MS);

                OutputStream backendOut = backendSocket.getOutputStream();
                backendOut.write(requestBytes);
                backendOut.flush();
                InputStream  backendIn  = backendSocket.getInputStream();
                OutputStream clientOut  = clientSocket.getOutputStream();
                pipe(backendIn, clientOut);

            } catch (IOException e) {
                System.err.printf("[%s] ERROR: backend %s unreachable: %s%n",
                        Instant.now(), backend, e.getMessage());
                sendBadGateway(clientSocket);
            }

        } catch (IOException e) {
            System.err.printf("[%s] ERROR: client %s: %s%n",
                    Instant.now(), clientAddr, e.getMessage());
        }
    }

    private byte[] readFromSocket(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[BUFFER_SIZE];
        int bytesRead;

        while ((bytesRead = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, bytesRead);
            String soFar = buffer.toString(StandardCharsets.UTF_8);
            if (soFar.contains("\r\n\r\n")) {
                break;
            }
        }

        return buffer.toByteArray();
    }

    private void pipe(InputStream src, OutputStream dst) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        while ((bytesRead = src.read(buffer)) != -1) {
            dst.write(buffer, 0, bytesRead);
        }
        dst.flush();
    }

    private String extractFirstLine(byte[] requestBytes) {
        String raw = new String(requestBytes, StandardCharsets.UTF_8);
        int end = raw.indexOf("\r\n");
        return end > 0 ? raw.substring(0, end) : raw.substring(0, Math.min(raw.length(), 80));
    }

    private void sendServiceUnavailable(Socket socket) throws IOException {
        String response = "HTTP/1.1 503 Service Unavailable\r\n" +
                "Content-Type: text/plain\r\n" +
                "Connection: close\r\n\r\n" +
                "503 Service Unavailable — no healthy backends\r\n";
        socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
    }

    private void sendBadGateway(Socket socket) {
        try {
            String response = "HTTP/1.1 502 Bad Gateway\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Connection: close\r\n\r\n" +
                    "502 Bad Gateway — backend unreachable\r\n";
            socket.getOutputStream().write(response.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
    }
}
