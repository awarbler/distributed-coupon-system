package coupons.api;

import java.io.IOException;

/** Retry journal owned by the HTTP boundary, independent of receipt write requests. */
interface ApiRequestStore {
    record Result(int status, String body) {}
    @FunctionalInterface interface Write { Result run() throws IOException; }
    Result execute(String scope, String key, String signature, Write write) throws IOException;
    final class Conflict extends RuntimeException {
        Conflict(String message) { super(message); }
    }
}
