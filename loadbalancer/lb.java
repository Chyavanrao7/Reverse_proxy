import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.*;

public class loadbalancer {
    private static final int LISTEN_PORT = 8888;
    private static final List<InetSocketAddress> backendServers = Arrays.asList(
    new InetSocketAddress("backend1", 9001),
    new InetSocketAddress("backend2", 9002),
    new InetSocketAddress("backend3", 9003)
);
    private static int currentIndex = 0;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(LISTEN_PORT);
        System.out.println("Load Balancer running on port " + LISTEN_PORT);

        ExecutorService threadPool = Executors.newCachedThreadPool();

        while (true) {
            Socket clientSocket = serverSocket.accept();
            InetSocketAddress backend = getNextBackend();
            threadPool.submit(() -> handleConnection(clientSocket, backend));
        }
    }

    private static synchronized InetSocketAddress getNextBackend() {
        InetSocketAddress selected = backendServers.get(currentIndex);
        currentIndex = (currentIndex + 1) % backendServers.size();
        return selected;
    }

    private static void handleConnection(Socket clientSocket, InetSocketAddress backend) {
        try (
            Socket backendSocket = new Socket(backend.getHostName(), backend.getPort())
        ) {
            System.out.println("Routing to: " + backend);
            Thread t1 = new Thread(() -> forwardData(clientSocket, backendSocket));
            Thread t2 = new Thread(() -> forwardData(backendSocket, clientSocket));
            t1.start();
            t2.start();
            t1.join();
            t2.join();
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
        } finally {
            try { clientSocket.close(); } catch (IOException ignored) {}
        }
    }

    private static void forwardData(Socket src, Socket dest) {
        try (
            InputStream in = src.getInputStream();
            OutputStream out = dest.getOutputStream()
        ) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        } catch (IOException ignored) {}
    }
}
