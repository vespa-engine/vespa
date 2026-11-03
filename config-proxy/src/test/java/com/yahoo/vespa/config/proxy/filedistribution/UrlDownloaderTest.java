package com.yahoo.vespa.config.proxy.filedistribution;

import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UrlDownloaderTest {

    private static final String TOKEN = "secret";
    private static final String BEARER = "Bearer " + TOKEN;

    @TempDir
    public File temporaryFolder;

    private final List<TestServer> servers = new ArrayList<>();

    @AfterEach
    public void stopServers() {
        servers.forEach(TestServer::stop);
    }

    @Test
    public void testFileDownload() throws IOException {
        URI uri = URI.create("https://docs.vespa.ai/foo");
        var downloader = new UrlDownloader(uri, new DownloadOptions(null));

        assertFalse(downloader.alreadyDownloaded(temporaryFolder));

        Files.write(temporaryFolder.toPath().resolve("foo"), "bar".getBytes(StandardCharsets.UTF_8));
        assertTrue(downloader.alreadyDownloaded(temporaryFolder));
    }

    @Test
    public void followsCrossOriginRedirectAndOnlySendsTokenToOriginalOrigin() throws IOException {
        var cdn = server().ok("/model.onnx", "content");
        var origin = server().redirect("/resolve/model.onnx", cdn.uri("/model.onnx"));

        var file = download(origin.uri("/resolve/model.onnx"), TOKEN).orElseThrow();

        assertEquals("content", Files.readString(file.toPath()));
        assertEquals(Optional.of(BEARER), origin.authHeaderSentTo("/resolve/model.onnx"));
        assertEquals(Optional.empty(), cdn.authHeaderSentTo("/model.onnx"));
    }

    @Test
    public void keepsTokenOnSameOriginRedirect() throws IOException {
        var origin = server().redirect("/resolve/model.onnx", "/moved/model.onnx")
                             .ok("/moved/model.onnx", "content");

        var file = download(origin.uri("/resolve/model.onnx"), TOKEN).orElseThrow();

        assertEquals("content", Files.readString(file.toPath()));
        assertEquals(Optional.of(BEARER), origin.authHeaderSentTo("/resolve/model.onnx"));
        assertEquals(Optional.of(BEARER), origin.authHeaderSentTo("/moved/model.onnx"));
    }

    @Test
    public void failsOnTooManyRedirects() throws IOException {
        var server = server();
        for (int i = 1; i <= 6; i++)
            server.redirect("/hop" + i, "/hop" + (i + 1));
        server.ok("/hop7", "content");

        var e = assertThrows(IOException.class, () -> download(server.uri("/hop1"), null));

        assertTrue(e.getMessage().contains("Maximum redirects (5) exceeded"), e.getMessage());
        assertFalse(server.wasRequested("/hop7"));
    }

    @Test
    public void redirectStrategyStillRefusesCrossOriginWithOtherSensitiveHeaders() {
        var request = new BasicClassicHttpRequest("GET", "/model.onnx");
        request.setHeader("Authorization", BEARER);
        request.setHeader("Cookie", "session=1");

        var allowed = new UrlDownloader.StripAuthOnCrossOriginRedirectStrategy()
                .isRedirectAllowed(new HttpHost("https", "a.example", 443), new HttpHost("https", "b.example", 443),
                                   request, HttpClientContext.create());

        assertFalse(allowed);
        assertFalse(request.containsHeader("Authorization"));
        assertTrue(request.containsHeader("Cookie"));
    }

    private Optional<File> download(URI uri, String token) throws IOException {
        return new UrlDownloader(uri, new DownloadOptions(token)).download(temporaryFolder);
    }

    private TestServer server() throws IOException {
        var server = new TestServer();
        servers.add(server);
        return server;
    }

    /** A local http server where each path either redirects or serves a body, recording the Authorization header it received. */
    private static class TestServer {

        private final HttpServer server;
        private final Map<String, Optional<String>> authHeaders = new ConcurrentHashMap<>();

        TestServer() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.start();
        }

        TestServer redirect(String path, String location) {
            return handle(path, 302, location, "");
        }

        TestServer redirect(String path, URI location) {
            return redirect(path, location.toString());
        }

        TestServer ok(String path, String body) {
            return handle(path, 200, null, body);
        }

        private TestServer handle(String path, int code, String location, String body) {
            server.createContext(path, exchange -> {
                authHeaders.put(path, Optional.ofNullable(exchange.getRequestHeaders().getFirst("Authorization")));
                if (location != null) exchange.getResponseHeaders().add("Location", location);
                var bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            return this;
        }

        URI uri(String path) {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        }

        boolean wasRequested(String path) {
            return authHeaders.containsKey(path);
        }

        /** The Authorization header received on the last request to path; fails if path was never requested */
        Optional<String> authHeaderSentTo(String path) {
            assertTrue(wasRequested(path), "Expected a request to " + path);
            return authHeaders.get(path);
        }

        void stop() {
            server.stop(0);
        }

    }

}
