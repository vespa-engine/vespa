// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.proxy;

import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Acceptor;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Request;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.StringValue;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Target;
import com.yahoo.jrt.Transport;
import com.yahoo.vespa.config.RawConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author hmusum
 * @author bjorncs
 */
public class ConfigProxyRpcServerTest {
    private static final String hostname = "localhost";
    private static final int port = 12345;
    private static final String configSourceAddress = "tcp/" + hostname + ":" + port;
    private static TestServer server;
    private static TestClient client;

    @TempDir
    public File temporaryFolder;

    @BeforeAll
    public static void setup() throws ListenFailedException {
        server = new TestServer();
        client = new TestClient(server.listenPort());
    }

    @AfterAll
    public static void teardown() {
        client.close();
        server.close();
    }

    private static void reset() throws ListenFailedException {
        teardown();
        setup();
    }

    @Test
    void basic() {
        ProxyServer proxy = createTestServer(new MockConfigSource());
        Spec spec = new Spec("localhost", 12345);
        ConfigProxyRpcServer server = new ConfigProxyRpcServer(proxy, new Supervisor(new Transport()), spec);
        assertEquals(spec, server.getSpec());
    }

    /**
     * Tests ping RPC command
     */
    @Test
    void testRpcMethodPing() {
        Request req = new Request("ping");
        client.invoke(req);

        assertFalse(req.isError(), req.errorMessage());
        assertEquals(1, req.returnValues().size());
        assertEquals(0, req.returnValues().get(0).asInt32());
    }

    /**
     * Tests listCachedConfig RPC command
     */
    @Test
    void testRpcMethodListCachedConfig() throws ListenFailedException {
        reset();

        Request req = new Request("listCachedConfig");
        client.invoke(req);

        assertFalse(req.isError(), req.errorMessage());
        String[] ret = req.returnValues().get(0).asStringArray();
        assertEquals(1, req.returnValues().size());
        assertEquals(0, ret.length);

        final RawConfig config = ProxyServerTest.fooConfig;
        server.proxyServer().memoryCache().update(config);
        req = new Request("listCachedConfig");
        client.invoke(req);
        assertFalse(req.isError(), req.errorMessage());
        assertEquals(1, req.returnValues().size());
        ret = req.returnValues().get(0).asStringArray();
        assertEquals(1, ret.length);
        assertEquals(config.getNamespace() + "." + config.getName() + "," + config.getConfigId() + "," +
                                 config.getGeneration() + "," + config.getPayloadChecksums(),
                                 ret[0]);
    }

    /**
     * Tests listCachedConfig RPC command
     */
    @Test
    void testRpcMethodListCachedConfigFull() {
        Request req = new Request("listCachedConfigFull");
        client.invoke(req);

        assertFalse(req.isError(), req.errorMessage());
        assertEquals(1, req.returnValues().size());
        String[] ret = req.returnValues().get(0).asStringArray();
        assertEquals(0, ret.length);

        final RawConfig config = ProxyServerTest.fooConfig;
        server.proxyServer().memoryCache().update(config);
        req = new Request("listCachedConfigFull");
        client.invoke(req);
        assertFalse(req.isError(), req.errorMessage());
        ret = req.returnValues().get(0).asStringArray();
        assertEquals(1, ret.length);
        assertEquals(config.getNamespace() + "." + config.getName() + "," + config.getConfigId() + "," +
                                 config.getGeneration() + "," + config.getPayloadChecksums() + "," + config.getPayload().getData(),
                                 ret[0]);
    }

    /**
     * Tests listSourceConnections RPC command
     */
    @Test
    void testRpcMethodListSourceConnections() throws ListenFailedException {
        reset();

        Request req = new Request("listSourceConnections");
        client.invoke(req);

        assertFalse(req.isError(), req.errorMessage());
        assertEquals(1, req.returnValues().size());
        final String[] ret = req.returnValues().get(0).asStringArray();
        assertEquals(2, ret.length);
        assertEquals("Current source: " + configSourceAddress, ret[0]);
        assertEquals("All sources:\n" + configSourceAddress + "\n", ret[1]);
    }

    /**
     * Tests invalidateCache RPC command
     */
    @Test
    void testRpcMethodInvalidateCache() {
        Request req = new Request("invalidateCache");
        client.invoke(req);

        assertFalse(req.isError(), req.errorMessage());
        assertEquals(1, req.returnValues().size());
        final String[] ret = req.returnValues().get(0).asStringArray();
        assertEquals(2, ret.length);
        assertEquals("0", ret[0]);
        assertEquals("success", ret[1]);
    }

    /**
     * Tests getMode and setMode RPC commands
     */
    @Test
    void testRpcMethodGetModeAndSetMode() {
        Request req = new Request("getMode");
        client.invoke(req);
        assertFalse(req.isError(), req.errorMessage());
        assertEquals(1, req.returnValues().size());
        assertEquals("default", req.returnValues().get(0).asString());

        req = new Request("setMode");
        String mode = "memorycache";
        req.parameters().add(new StringValue(mode));
        client.invoke(req);
        assertFalse(req.isError(), req.errorMessage());
        assertEquals(1, req.returnValues().size());
        String[] ret = req.returnValues().get(0).asStringArray();
        assertEquals(2, ret.length);
        assertEquals("0", ret[0]);
        assertEquals("success", ret[1]);
        assertEquals(mode, server.proxyServer().getMode().name());

        req = new Request("getMode");
        client.invoke(req);
        assertFalse(req.isError(), req.errorMessage());
        assertEquals(1, req.returnValues().size());
        assertEquals(mode, req.returnValues().get(0).asString());

        req = new Request("setMode");
        String oldMode = mode;
        mode = "invalid";
        req.parameters().add(new StringValue(mode));
        client.invoke(req);

        assertFalse(req.isError(), req.errorMessage());
        ret = req.returnValues().get(0).asStringArray();
        assertEquals(2, ret.length);
        assertEquals("1", ret[0]);
        assertEquals("Unrecognized mode '" + mode + "' supplied. Legal modes are '" + Mode.modes() + "'", ret[1]);
        assertEquals(oldMode, server.proxyServer().getMode().name());
    }

    /**
     * Tests updateSources RPC command
     */
    @Test
    void testRpcMethodUpdateSources() throws ListenFailedException {
        reset();

        Request req = new Request("updateSources");
        String spec1 = "tcp/a:19070";
        String spec2 = "tcp/b:19070";
        req.parameters().add(new StringValue(spec1 + "," + spec2));
        client.invoke(req);
        assertFalse(req.isError(), req.errorMessage());
        assertEquals(1, req.returnValues().size());
        assertEquals("Updated config sources to: " + spec1 + "," + spec2, req.returnValues().get(0).asString());


        server.proxyServer().setMode(Mode.ModeName.MEMORYCACHE.name());

        req = new Request("updateSources");
        req.parameters().add(new StringValue(spec1 + "," + spec2));
        client.invoke(req);
        assertFalse(req.isError(), req.errorMessage());
        assertEquals(1, req.returnValues().size());
        assertEquals("Cannot update sources when in '" + Mode.ModeName.MEMORYCACHE.name().toLowerCase() + "' mode", req.returnValues().get(0).asString());

        // TODO source connections needs to have deterministic order to work
        /*req = new Request("listSourceConnections");
        rpcServer.listSourceConnections(req);
        assertFalse(req.errorMessage(), req.isError());
        final String[] ret = req.returnValues().get(0).asStringArray();
        assertEquals(ret.length, is(2));
        assertEquals(ret[0], is("Current source: " + spec1));
        assertEquals(ret[1], is("All sources:\n" + spec2 + "\n" + spec1 + "\n"));
        */
    }

    /**
     * Tests dumpCache RPC command
     */
    @Test
    void testRpcMethodDumpCache() throws IOException {
        Request req = new Request("dumpCache");
        String path = temporaryFolder.getAbsolutePath();
        req.parameters().add(new StringValue(path));
        client.invoke(req);
        assertFalse(req.isError(), req.errorMessage());
        assertEquals(1, req.returnValues().size());
        assertEquals("success", req.returnValues().get(0).asString());
    }

    private static ProxyServer createTestServer(ConfigSourceSet source) {
        return new ProxyServer(null, source, new RpcConfigSourceClient(new ResponseHandler(), source));
    }

    private static class TestServer implements AutoCloseable {

        private static final Spec SPEC = new Spec(0);

        private final ProxyServer proxyServer = createTestServer(new ConfigSourceSet(configSourceAddress));
        private final Supervisor supervisor = new Supervisor(new Transport());
        private final ConfigProxyRpcServer rpcServer = new ConfigProxyRpcServer(proxyServer, supervisor, SPEC);
        private final Acceptor acceptor;

        TestServer() throws ListenFailedException {
            acceptor = supervisor.listen(SPEC);
        }

        ProxyServer proxyServer() {
            return proxyServer;
        }

        int listenPort() {
            return acceptor.port();
        }

        @Override
        public void close() {
            acceptor.shutdown().join();
            supervisor.transport().shutdown().join();
            rpcServer.shutdown();
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }
    }

    private static class TestClient implements AutoCloseable {

        private final Supervisor supervisor;
        private final Target target;

        TestClient(int rpcPort) {
            this.supervisor = new Supervisor(new Transport());
            this.target = supervisor.connect(new Spec(rpcPort));
        }

        void invoke(Request request) {
            target.invokeSync(request, Duration.ofMinutes(10));
        }

        @Override
        public void close() {
            target.close();
            supervisor.transport().shutdown().join();
        }

        private static File newFolder(File root, String... subDirs) throws IOException {
            String subFolder = String.join("/", subDirs);
            File result = new File(root, subFolder);
            if (!result.mkdirs()) {
                throw new IOException("Couldn't create folders " + root);
            }
            return result;
        }
    }

    private static File newFolder(File root, String... subDirs) throws IOException {
        String subFolder = String.join("/", subDirs);
        File result = new File(root, subFolder);
        if (!result.mkdirs()) {
            throw new IOException("Couldn't create folders " + root);
        }
        return result;
    }
}
