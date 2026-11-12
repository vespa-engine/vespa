// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.sun.net.httpserver.HttpServer;
import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.Int32Value;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.yahoo.vespa.config.UrlDownloader.HTTP_ERROR;
import static com.yahoo.vespa.config.UrlDownloader.INTERNAL_ERROR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UrlDownloadRpcServerTest {

    @TempDir
    public File downloadDirectory;

    private Supervisor serverSupervisor;
    private Supervisor clientSupervisor;
    private Acceptor acceptor;
    private UrlDownloadRpcServer rpcServer;
    private Target target;

    @BeforeEach
    public void setup() throws ListenFailedException {
        serverSupervisor = new Supervisor(new Transport());
        rpcServer = new UrlDownloadRpcServer(serverSupervisor, downloadDirectory);
        acceptor = serverSupervisor.listen(new Spec(0));
        clientSupervisor = new Supervisor(new Transport());
        target = clientSupervisor.connect(new Spec(acceptor.port()));
    }

    @AfterEach
    public void teardown() {
        target.close();
        clientSupervisor.transport().shutdown().join();
        acceptor.shutdown().join();
        rpcServer.close();
        serverSupervisor.transport().shutdown().join();
    }

    @Test
    void urlIsDownloadedOnceAndThenAnsweredFromTheDownloadDirectory() throws IOException {
        var httpRequests = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model.onnx", exchange -> {
            httpRequests.incrementAndGet();
            var body = "content".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            for (int i = 0; i < 2; i++) {
                Request request = new Request("url.waitFor");
                request.parameters().add(new StringValue("http://127.0.0.1:" + server.getAddress().getPort() + "/model.onnx"));
                target.invokeSync(request, Duration.ofSeconds(10));

                assertFalse(request.isError(), request.errorMessage());
                var file = new File(request.returnValues().get(0).asString());
                assertEquals("model.onnx", file.getName());
                assertEquals(downloadDirectory, file.getParentFile().getParentFile());
                assertEquals("content", Files.readString(file.toPath()));
            }
            assertEquals(1, httpRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invalidUrlIsAnsweredWithAnError() {
        for (var url : List.of("ftp://my.website.invalid/model.onnx",
                               "https://my.website.invalid/model with space.onnx",
                               "model.onnx")) {
            Request request = new Request("url.waitFor");
            request.parameters().add(new StringValue(url));
            target.invokeSync(request, Duration.ofSeconds(10));

            assertTrue(request.isError(), url);
            // A request that is never answered times out instead
            assertEquals(HTTP_ERROR, request.errorCode(), url + ": " + request.errorMessage());
        }
    }

    @Test
    void failureWithoutMessageIsAnsweredWithAnError() throws IOException {
        // The body ends inside the gzip header, so reading it fails with an EOFException that has no message
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/model.onnx", exchange -> {
            exchange.getResponseHeaders().add("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, 3);
            exchange.getResponseBody().write(new byte[] { 0x1f, (byte) 0x8b, 8 });
            exchange.close();
        });
        server.start();
        try {
            Request request = new Request("url.waitFor");
            request.parameters().add(new StringValue("http://127.0.0.1:" + server.getAddress().getPort() + "/model.onnx"));
            target.invokeSync(request, Duration.ofSeconds(10));

            assertTrue(request.isError());
            assertEquals(INTERNAL_ERROR, request.errorCode(), request.errorMessage());
            assertEquals("EOFException", request.errorMessage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void authTokenOfWrongTypeIsAnsweredWithAnError() {
        Request request = new Request("url.waitFor");
        request.parameters().add(new StringValue("https://my.website.invalid/model.onnx"));
        request.parameters().add(new Int32Value(1));
        target.invokeSync(request, Duration.ofSeconds(10));

        assertTrue(request.isError());
        assertEquals(HTTP_ERROR, request.errorCode(), request.errorMessage());
    }

}
