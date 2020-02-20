// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.time.Duration;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 * @author bjorncs
 */
public class ConfigProxyRpcServerTest {
    private static final String hostname = "localhost";
    private static final int port = 12345;
    private static final String configSourceAddress = "tcp/" + hostname + ":" + port;
    private TestServer server;
    private TestClient client;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws ListenFailedException {
        server = new TestServer();
        client = new TestClient(server.listenPort());
    }

    @After
    public void teardown() {
        client.close();
        server.close();
    }

    @Test
    public void basic() {
        ProxyServer proxy = createTestServer(new MockConfigSource());
        Spec spec = new Spec("localhost", 12345);
        ConfigProxyRpcServer server = new ConfigProxyRpcServer(proxy, new Supervisor(new Transport()), spec);
        assertThat(server.getSpec(), is(spec));
    }

    /**
     * Tests ping RPC command
     */
    @Test
    public void testRpcMethodPing() {
        Request req = new Request("ping");
        client.invoke(req);

        assertFalse(req.errorMessage(), req.isError());
        assertThat(req.returnValues().size(), is(1));
        assertThat(req.returnValues().get(0).asInt32(), is(0));
    }

    /**
     * Tests listCachedConfig RPC command
     */
    @Test
    public void testRpcMethodListCachedConfig() {
        Request req = new Request("listCachedConfig");
        client.invoke(req);

        assertFalse(req.errorMessage(), req.isError());
        String[] ret = req.returnValues().get(0).asStringArray();
        assertThat(req.returnValues().size(), is(1));
        assertThat(ret.length, is(0));

        final RawConfig config = ProxyServerTest.fooConfig;
        server.proxyServer().getMemoryCache().update(config);
        req = new Request("listCachedConfig");
        client.invoke(req);
        assertFalse(req.errorMessage(), req.isError());
        assertThat(req.returnValues().size(), is(1));
        ret = req.returnValues().get(0).asStringArray();
        assertThat(ret.length, is(1));
        assertThat(ret[0], is(config.getNamespace() + "." + config.getName() + "," +
                config.getConfigId() + "," +
                config.getGeneration() + "," +
                config.getConfigMd5()));
    }

    /**
     * Tests listCachedConfig RPC command
     */
    @Test
    public void testRpcMethodListCachedConfigFull() {
        Request req = new Request("listCachedConfigFull");
        client.invoke(req);

        assertFalse(req.errorMessage(), req.isError());
        assertThat(req.returnValues().size(), is(1));
        String[] ret = req.returnValues().get(0).asStringArray();
        assertThat(ret.length, is(0));

        final RawConfig config = ProxyServerTest.fooConfig;
        server.proxyServer().getMemoryCache().update(config);
        req = new Request("listCachedConfigFull");
        client.invoke(req);
        assertFalse(req.errorMessage(), req.isError());
        ret = req.returnValues().get(0).asStringArray();
        assertThat(ret.length, is(1));
        assertThat(ret[0], is(config.getNamespace() + "." + config.getName() + "," +
                config.getConfigId() + "," +
                config.getGeneration() + "," +
                config.getConfigMd5() + "," +
                config.getPayload().getData()));
    }

    /**
     * Tests printStatistics RPC command
     */
    @Test
    public void testRpcMethodListSourceConnections() {
        Request req = new Request("listSourceConnections");
        client.invoke(req);

        assertFalse(req.errorMessage(), req.isError());
        assertThat(req.returnValues().size(), is(1));
        final String[] ret = req.returnValues().get(0).asStringArray();
        assertThat(ret.length, is(2));
        assertThat(ret[0], is("Current source: " + configSourceAddress));
        assertThat(ret[1], is("All sources:\n" + configSourceAddress + "\n"));
    }

    /**
     * Tests printStatistics RPC command
     */
    @Test
    public void testRpcMethodPrintStatistics() {
        Request req = new Request("printStatistics");
        client.invoke(req);
        assertFalse(req.errorMessage(), req.isError());
        assertThat(req.returnValues().size(), is(1));
        assertThat(req.returnValues().get(0).asString(), is("\n" +
                "Delayed responses queue size: 0\n" +
                "Contents: "));
    }

    /**
     * Tests invalidateCache RPC command
     */
    @Test
    public void testRpcMethodInvalidateCache() {
        Request req = new Request("invalidateCache");
        client.invoke(req);

        assertFalse(req.errorMessage(), req.isError());
        assertThat(req.returnValues().size(), is(1));
        final String[] ret = req.returnValues().get(0).asStringArray();
        assertThat(ret.length, is(2));
        assertThat(ret[0], is("0"));
        assertThat(ret[1], is("success"));
    }

    /**
     * Tests getMode and setMode RPC commands
     */
    @Test
    public void testRpcMethodGetModeAndSetMode() {
        Request req = new Request("getMode");
        client.invoke(req);
        assertFalse(req.errorMessage(), req.isError());
        assertThat(req.returnValues().size(), is(1));
        assertThat(req.returnValues().get(0).asString(), is("default"));

        req = new Request("setMode");
        String mode = "memorycache";
        req.parameters().add(new StringValue(mode));
        client.invoke(req);
        assertFalse(req.errorMessage(), req.isError());
        assertThat(req.returnValues().size(), is(1));
        String[] ret = req.returnValues().get(0).asStringArray();
        assertThat(ret.length, is(2));
        assertThat(ret[0], is("0"));
        assertThat(ret[1], is("success"));
        assertThat(server.proxyServer().getMode().name(), is(mode));

        req = new Request("getMode");
        client.invoke(req);
        assertFalse(req.errorMessage(), req.isError());
        assertThat(req.returnValues().size(), is(1));
        assertThat(req.returnValues().get(0).asString(), is(mode));

        req = new Request("setMode");
        String oldMode = mode;
        mode = "invalid";
        req.parameters().add(new StringValue(mode));
        client.invoke(req);

        assertFalse(req.errorMessage(), req.isError());
        ret = req.returnValues().get(0).asStringArray();
        assertThat(ret.length, is(2));
        assertThat(ret[0], is("1"));
        assertThat(ret[1], is("Unrecognized mode '" + mode + "' supplied. Legal modes are '" + Mode.modes() + "'"));
        assertThat(server.proxyServer().getMode().name(), is(oldMode));
    }

    /**
     * Tests updateSources RPC command
     */
    @Test
    public void testRpcMethodUpdateSources() {
        Request req = new Request("updateSources");
        String spec1 = "tcp/a:19070";
        String spec2 = "tcp/b:19070";
        req.parameters().add(new StringValue(spec1 + "," + spec2));
        client.invoke(req);
        assertFalse(req.errorMessage(), req.isError());
        assertThat(req.returnValues().size(), is(1));
        assertThat(req.returnValues().get(0).asString(), is("Updated config sources to: " + spec1 + "," + spec2));


        server.proxyServer().setMode(Mode.ModeName.MEMORYCACHE.name());

        req = new Request("updateSources");
        req.parameters().add(new StringValue(spec1 + "," + spec2));
        client.invoke(req);
        assertFalse(req.errorMessage(), req.isError());
        assertThat(req.returnValues().size(), is(1));
        assertThat(req.returnValues().get(0).asString(), is("Cannot update sources when in '" + Mode.ModeName.MEMORYCACHE.name().toLowerCase() + "' mode"));

        // TODO source connections needs to have deterministic order to work
        /*req = new Request("listSourceConnections");
        rpcServer.listSourceConnections(req);
        assertFalse(req.errorMessage(), req.isError());
        final String[] ret = req.returnValues().get(0).asStringArray();
        assertThat(ret.length, is(2));
        assertThat(ret[0], is("Current source: " + spec1));
        assertThat(ret[1], is("All sources:\n" + spec2 + "\n" + spec1 + "\n"));
        */
    }

    /**
     * Tests dumpCache RPC command
     */
    @Test
    public void testRpcMethodDumpCache() throws IOException {
        Request req = new Request("dumpCache");
        String path = temporaryFolder.newFolder().getAbsolutePath();
        req.parameters().add(new StringValue(path));
        client.invoke(req);
        assertFalse(req.errorMessage(), req.isError());
        assertThat(req.returnValues().size(), is(1));
        assertThat(req.returnValues().get(0).asString(), is("success"));
    }

    private static ProxyServer createTestServer(ConfigSourceSet source) {
        return new ProxyServer(null, source, new MemoryCache(), null);
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
    }

    private static class TestClient implements AutoCloseable {

        private final Supervisor supervisor;
        private final Target target;

        TestClient(int rpcPort) {
            this.supervisor = new Supervisor(new Transport());
            this.target = supervisor.connect(new Spec(rpcPort));
        }

        void invoke(Request request) {
            target.invokeSync(request, Duration.ofMinutes(10).getSeconds());
        }

        @Override
        public void close() {
            target.close();
            supervisor.transport().shutdown().join();
        }
    }
}
