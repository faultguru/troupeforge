package com.troupeforge.tools.web;

import com.troupeforge.core.tool.Tool;
import com.troupeforge.core.tool.ToolContext;
import com.troupeforge.core.tool.ToolParam;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches content from a URL using HTTP GET or POST.
 */
public class WebFetchTool implements Tool {

    public static final String NAME = "web_fetch";

    private static final int DEFAULT_TIMEOUT_SECONDS = 15;
    private static final int MAX_BODY_LENGTH = 50000;

    public record Request(
        @ToolParam(description = "The URL to fetch")
        String url,
        @ToolParam(description = "HTTP method: GET or POST (default GET)", required = false)
        String method,
        @ToolParam(description = "Request body for POST requests", required = false)
        String body,
        @ToolParam(description = "Timeout in seconds (default 15)", required = false)
        Integer timeoutSeconds
    ) {}

    public record Response(int statusCode, String contentType, String body, long contentLength) {}

    @Override public String name() { return NAME; }
    @Override public String description() { return "Fetch content from a URL"; }
    @Override public Class<Request> requestType() { return Request.class; }
    @Override public Class<Response> responseType() { return Response.class; }

    @Override
    public Record execute(ToolContext context, Record request) {
        var req = (Request) request;
        int timeout = req.timeoutSeconds() != null ? req.timeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
        String httpMethod = req.method() != null ? req.method().toUpperCase() : "GET";

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(req.url()))
                    .timeout(Duration.ofSeconds(timeout));

            if ("POST".equals(httpMethod) && req.body() != null) {
                requestBuilder.POST(HttpRequest.BodyPublishers.ofString(req.body()));
            } else {
                requestBuilder.GET();
            }

            HttpResponse<String> response = client.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            String responseBody = response.body();
            long contentLength = responseBody != null ? responseBody.length() : 0;
            if (responseBody != null && responseBody.length() > MAX_BODY_LENGTH) {
                responseBody = responseBody.substring(0, MAX_BODY_LENGTH) + "\n... [truncated]";
            }

            String contentType = response.headers()
                    .firstValue("Content-Type")
                    .orElse("unknown");

            return new Response(response.statusCode(), contentType, responseBody, contentLength);
        } catch (Exception e) {
            return new Response(0, "error", "Error fetching URL: " + e.getMessage(), 0);
        }
    }
}
