// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy.filedistribution;

import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.RequestWaiter;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.filedistribution.FileDownloader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author hmusum
 */
public class FileDistributionRpcServerTest {

    // Duplicated from FileDistributionRpcServer/FileAcquirerImpl.FileDistributionErrorCode
    private static final int fileReferencePermissionDenied = 0x11001;

    // Duplicated from MultiTenantRpcAuthorizer.JrtErrorCode / FileReferenceDownloader
    private static final int jrtErrorUnauthorized = 0x20001;

    @TempDir
    public File downloadDirectory;

    private Supervisor serverSupervisor;
    private Supervisor clientSupervisor;
    private Acceptor acceptor;
    private FileDistributionRpcServer rpcServer;
    private FakeConnection fakeConnection;
    private Target target;

    @BeforeEach
    public void setup() throws ListenFailedException {
        fakeConnection = new FakeConnection();
        FileDownloader downloader = new FileDownloader(new FakeConnectionPool(fakeConnection),
                                                        new Supervisor(new Transport()),
                                                        downloadDirectory,
                                                        Duration.ofSeconds(30), // Much longer than this test should ever take
                                                        Duration.ofMillis(10));
        serverSupervisor = new Supervisor(new Transport());
        rpcServer = new FileDistributionRpcServer(serverSupervisor, downloader);
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
    void permissionDeniedIsReturnedImmediatelyAsPermanentError() {
        Request req = new Request("waitFor");
        req.parameters().add(new StringValue("myFileReference"));
        target.invokeSync(req, Duration.ofSeconds(10));

        assertTrue(req.isError(), "Expected an error response, got: " + req.returnValues());
        assertEquals(fileReferencePermissionDenied, req.errorCode());
        assertTrue(req.errorMessage().contains("Peer is not allowed to access file reference"), req.errorMessage());

        // Must fail fast - a single RPC round trip to the (fake) source, not retried for the full download timeout
        assertEquals(1, fakeConnection.requestCount);
    }

    private static class FakeConnection implements Connection {

        int requestCount = 0;

        @Override
        public void invokeAsync(Request request, Duration jrtTimeout, RequestWaiter requestWaiter) {
            invokeSync(request, jrtTimeout);
            requestWaiter.handleRequestDone(request);
        }

        @Override
        public void invokeSync(Request request, Duration jrtTimeout) {
            if (request.methodName().equals("filedistribution.serveFile")) {
                requestCount++;
                request.setError(jrtErrorUnauthorized,
                                  "Peer is not allowed to access file reference " + request.parameters().get(0).asString());
            }
        }

        @Override
        public String getAddress() { return "localhost"; }

    }

    private static class FakeConnectionPool implements ConnectionPool {

        private final Connection connection;

        FakeConnectionPool(Connection connection) { this.connection = connection; }

        @Override
        public void close() {}

        @Override
        public Connection getCurrent() { return connection; }

        @Override
        public Connection switchConnection(Connection failingConnection) { return connection; }

        @Override
        public int getSize() { return 1; }

        @Override
        public List<Connection> connections() { return List.of(connection); }

    }

}
