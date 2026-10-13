package com.yahoo.vespa.config.proxy.filedistribution;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UrlDownloaderTest {

    @TempDir
    public File temporaryFolder;

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
        var cdnAuth = new AtomicReference<String>("not called");
        var originAuth = new AtomicReference<String>("not called");
        var cdn = server("/model.onnx", 200, null, "content", cdnAuth);
        var cdnUri = "http://127.0.0.1:" + cdn.getAddress().getPort() + "/model.onnx";
        var origin = server("/resolve/model.onnx", 302, cdnUri, "", originAuth);
        try {
            var uri = URI.create("http://127.0.0.1:" + origin.getAddress().getPort() + "/resolve/model.onnx");
            var file = new UrlDownloader(uri, new DownloadOptions("secret")).download(temporaryFolder).orElseThrow();
            assertEquals("content", Files.readString(file.toPath()));
            assertEquals("Bearer secret", originAuth.get());
            assertNull(cdnAuth.get());
        } finally {
            origin.stop(0);
            cdn.stop(0);
        }
    }

    @Test
    public void failsOnTooManyRedirects() throws IOException {
        var server = server("/loop", 302, "/loop", "", new AtomicReference<>());
        try {
            var uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/loop");
            var e = assertThrows(IOException.class, () -> new UrlDownloader(uri, new DownloadOptions(null)).download(temporaryFolder));
            assertTrue(e.getMessage().contains("redirect"), e.getMessage());
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer server(String path, int code, String location, String body, AtomicReference<String> authHeader) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            if (location != null) exchange.getResponseHeaders().add("Location", location);
            var bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}
