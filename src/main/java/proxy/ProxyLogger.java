package proxy;

import java.time.Instant;

public class ProxyLogger {

    private static final String RESET  = "[0m";
    private static final String RED    = "[31m";
    private static final String YELLOW = "[33m";
    private static final String CYAN   = "[36m";
    private static final String GREEN  = "[32m";


    public static void request(String method, String path, String backend,
                               int status, long latencyMs, String requestId) {
        String color = (status >= 500) ? RED : (status >= 400) ? YELLOW : GREEN;
        System.out.printf("[%s] [REQUEST] %s %s → %s | %s%d%s | %dms | id=%s%n",
                Instant.now(), method, path, backend,
                color, status, RESET, latencyMs, requestId);
    }

    public static void info(String message) {
        System.out.printf("[%s] [%sINFO%s]  %s%n", Instant.now(), CYAN, RESET, message);
    }

    public static void warn(String message) {
        System.out.printf("[%s] [%sWARN%s]  %s%n", Instant.now(), YELLOW, RESET, message);
    }

    public static void error(String message) {
        System.out.printf("[%s] [%sERROR%s] %s%n", Instant.now(), RED, RESET, message);
    }

    public static void error(String message, Throwable cause) {
        error(message + " | " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
    }
}
