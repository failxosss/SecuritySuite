package com.example.securitysuite.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Thin wrapper around java.net.http.HttpClient used by every network-facing
 * component (IP providers, Discord webhook). All calls are non-blocking and
 * run on the supplied executor - never on the main server thread.
 */
public final class AsyncHttp {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private AsyncHttp() {}

    public static CompletableFuture<String> getAsync(String url, long timeoutMs, Executor executor) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .GET()
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApplyAsync(HttpResponse::body, executor);
    }

    public static CompletableFuture<Integer> postJsonAsync(String url, String json, long timeoutMs, Executor executor) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return CLIENT.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenApplyAsync(HttpResponse::statusCode, executor);
    }
}
