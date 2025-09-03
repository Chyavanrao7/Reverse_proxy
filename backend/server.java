import java.io.*;
import java.net.*;

public class HttpBackendServer {
    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 9001;
        ServerSocket serverSocket = new ServerSocket(port);
        System.out.println("HTTP Server started on port " + port);

        while (true) {
            Socket client = serverSocket.accept();
            new Thread(() -> handleClient(client, port)).start();
        }
    }

    private static void handleClient(Socket client, int port) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()))
        ) {
            // Read and print request (optional)
            String line;
            while (!(line = in.readLine()).isEmpty()) {
                System.out.println("[" + port + "] " + line);
            }

            String responseBody = "<h1>Hello from Backend Server on port " + port + "</h1>";
            String response = "HTTP/1.1 200 OK\r\n" +
                              "Content-Type: text/html\r\n" +
                              "Content-Length: " + responseBody.length() + "\r\n" +
                              "\r\n" +
                              responseBody;

            out.write(response);
            out.flush();
        } catch (IOException e) {
            System.err.println("Client error: " + e.getMessage());
        } finally {
            try { client.close(); } catch (IOException ignored) {}
        }
    }
}
