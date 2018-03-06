// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vdslib.distribution.Distribution;
import com.yahoo.vdslib.state.Node;
import com.yahoo.vdslib.state.NodeState;
import com.yahoo.vdslib.state.NodeType;
import com.yahoo.vdslib.state.State;
import com.yahoo.vespa.clustercontroller.core.status.StatusHandler;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageResponse;
import com.yahoo.vespa.clustercontroller.core.status.statuspage.StatusPageServer;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpRequest;
import com.yahoo.vespa.clustercontroller.utils.communication.http.HttpResult;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StatusPagesTest extends FleetControllerTest {

    public static Logger log = Logger.getLogger(StatusPagesTest.class.getName());

    private String doHttpGetRequest(String request, Date ifModifiedSince) throws IOException {
        int statusPort = fleetController.getHttpPort();
        Socket socket = new Socket("localhost", statusPort);

        BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        bw.write("GET " + request + " HTTP/1.1\r\n");
        if (ifModifiedSince != null) {
            DateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss z");
            df.setTimeZone(TimeZone.getTimeZone("GMT"));
            bw.write("If-Modified-Since: " + df.format(ifModifiedSince) + "\r\n");
        }
        bw.write("\r\n");
        bw.flush();

        InputStream stream = socket.getInputStream();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            byte [] buf = new byte[4096];
            while (true) {
                int read = stream.read(buf);
                if (read<=0) {
                    break;
                }
                output.write(buf, 0, read);
            }
            output.close();
            return output.toString();
        } finally {
            stream.close();
            bw.close();
        }
    }

    private String doHttpGetRequest(String request) throws IOException {
        return doHttpGetRequest(request, null);
    }

    @Test
    public void testStatusThroughContainer() throws Exception {
        startingTest("StatusPagesTest::testStatusThroughContainer()");
        FleetControllerOptions options = new FleetControllerOptions("mycluster");
        options.setStorageDistribution(new Distribution(Distribution.getDefaultDistributionConfig(3, 10)));
        final StatusHandler.ContainerStatusPageServer statusServer = new StatusHandler.ContainerStatusPageServer();
        setUpFleetController(true, options, true, statusServer);
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        //ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 100, 100, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(1000));
        //FleetControllerComponent fcComp = new FleetControllerComponent();
        //fcComp.addFleetController("mycluster", fleetController, statusServer);
        StatusHandler comp = new StatusHandler(new StatusHandler.ClusterStatusPageServerSet() {
            @Override
            public StatusHandler.ContainerStatusPageServer get(String cluster) {
                return ("mycluster".equals(cluster) ? statusServer : null);
            }

            @Override
            public Map<String, StatusHandler.ContainerStatusPageServer> getAll() {
                Map<String, StatusHandler.ContainerStatusPageServer> map = new HashMap<>();
                map.put("mycluster", statusServer);
                return map;
            }
        });

        {
            HttpRequest request = new HttpRequest().setPath("/clustercontroller-status/v1");
            HttpResult result = comp.handleRequest(request);
            assertEquals(result.toString(true), 200, result.getHttpReturnCode());
            assertEquals("<title>clusters</title>\n<a href=\"./mycluster\">mycluster</a><br>\n", result.getContent().toString());
        }
        {
            HttpRequest request = new HttpRequest().setPath("/clustercontroller-status/v1/");
            HttpResult result = comp.handleRequest(request);
            assertEquals(result.toString(true), 200, result.getHttpReturnCode());
            assertEquals("<title>clusters</title>\n<a href=\"./mycluster\">mycluster</a><br>\n", result.getContent().toString());
        }
        {
            HttpRequest request = new HttpRequest().setPath("/clustercontroller-status/v1/mycluster");
            HttpResult result = comp.handleRequest(request);
            assertEquals(result.toString(true), 200, result.getHttpReturnCode());
            assertTrue(result.toString(true), result.getContent().toString().contains(
                    "mycluster Cluster Controller 0 Status Page"));
        }
        {
            HttpRequest request = new HttpRequest().setPath("/clustercontroller-status/v1/mycluster/");
            HttpResult result = comp.handleRequest(request);
            assertEquals(result.toString(true), 200, result.getHttpReturnCode());
            assertTrue(result.toString(true), result.getContent().toString().contains(
                    "mycluster Cluster Controller 0 Status Page"));
            assertTrue(result.toString(true), result.getContent().toString().contains(
                    "href=\"mycluster/node=distributor.0\""));
            assertTrue(result.toString(true), result.getContent().toString().contains(
                    "href=\"mycluster/node=storage.0\""));
        }
        {
            HttpRequest request = new HttpRequest().setPath("/clustercontroller-status/v1/mycluster/node=storage.0");
            HttpResult result = comp.handleRequest(request);
            assertEquals(result.toString(true), 200, result.getHttpReturnCode());
            assertTrue(result.toString(true), result.getContent().toString().contains(
                    "Node status for storage.0"));
            assertTrue(result.toString(true), result.getContent().toString().contains(
                    "href=\"..\""));
        }
        {
            HttpRequest request = new HttpRequest().setPath("/clustercontroller-status/v1/foo");
            HttpResult result = comp.handleRequest(request);
            assertEquals(result.toString(true), 404, result.getHttpReturnCode());
        }
        {
            HttpRequest request = new HttpRequest().setPath("/foobar/v1/mycluster/");
            HttpResult result = comp.handleRequest(request);
            assertEquals(result.toString(true), 404, result.getHttpReturnCode());
        }
        {
            HttpRequest request = new HttpRequest().setPath("/clustercontroller-status/v2/");
            HttpResult result = comp.handleRequest(request);
            assertEquals(result.toString(true), 404, result.getHttpReturnCode());
        }
        //executor.shutdown();
    }

    @Test
    public void testZooKeeperAddressSplitting() {
        String rawAddress = "conc1.foo.yahoo.com:2181,conc2.foo.yahoo.com:2181,"
                          + "dp1.foo.yahoo.com:2181,dp2.foo.yahoo.com:2181,"
                          + "dp3.foo.yahoo.com:2181";
        String result = "conc1.foo.yahoo.com:2181, conc2.foo.yahoo.com:2181, "
                + "dp1.foo.yahoo.com:2181, dp2.foo.yahoo.com:2181, "
                + "dp3.foo.yahoo.com:2181";
        String split = FleetControllerOptions.splitZooKeeperAddress(rawAddress);
        assertEquals(result, split);
    }

    @Test
    public void testSimpleConnectionWithSomeContent() throws Exception {
        // Set this to true temporary if you want to check status page from browser. Should be false in checked in code always.
        boolean haltTestToViewStatusPage = false;
        startingTest("StatusPagesTest::testSimpleConnectionWithSomeContent()");
        FleetControllerOptions options = new FleetControllerOptions("mycluster");
        options.setStorageDistribution(new Distribution(Distribution.getDefaultDistributionConfig(3, 10)));
        //options.minRatioOfStorageNodesUp = 0.99;
        if (haltTestToViewStatusPage) {
            options.httpPort = 19234;
        }
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        nodes.get(2).disconnectBreakConnection();
        nodes.get(5).disconnectAsShutdown();
        nodes.get(7).disconnectSlobrok();

        fleetController.getCluster().getNodeInfo(new Node(NodeType.STORAGE, 3)).setWantedState(new NodeState(NodeType.STORAGE, State.MAINTENANCE).setDescription("Test&<>special"));

        String content = doHttpGetRequest("/");

        assertTrue(content, content.contains("<html>"));
        assertTrue(content, content.contains("</html>"));
        assertTrue(content, content.contains("Baseline cluster state"));
        assertTrue(content, content.contains("Cluster states"));
        assertTrue(content, content.contains("Event log"));

        if (haltTestToViewStatusPage) {
            System.err.println(content);
            try{
                Thread.sleep(1000000);
            } catch (InterruptedException e) {}
        }
    }

    @Test
    public void testNodePage() throws Exception {
        startingTest("StatusPagesTest::testNodePage()");
        FleetControllerOptions options = new FleetControllerOptions("mycluster");
        options.setStorageDistribution(new Distribution(Distribution.getDefaultDistributionConfig(3, 10)));
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        String content = doHttpGetRequest("/node=storage.0");

        assertTrue(content, content.contains("<html>"));
        assertTrue(content, content.contains("</html>"));
        assertTrue(content, content.contains("Node status for storage.0"));
        assertTrue(content, content.contains("REPORTED"));
        assertTrue(content, content.contains("Altered node state in cluster state from"));
        //System.err.println(sb.toString());

    }

    @Test
    public void testErrorResponseCode() throws Exception {
        startingTest("StatusPagesTest::testNodePage()");
        FleetControllerOptions options = new FleetControllerOptions("mycluster");
        options.setStorageDistribution(new Distribution(Distribution.getDefaultDistributionConfig(3, 10)));
        setUpFleetController(true, options);
        setUpVdsNodes(true, new DummyVdsNodeOptions());
        waitForStableSystem();

        String content = doHttpGetRequest("/fraggle/rock");

        assertTrue(content.contains("404 Not Found"));
        //System.err.println(sb.toString());
    }

    private StatusPageServer.HttpRequest makeHttpRequest(String request) {
        return new StatusPageServer.HttpRequest(request);
    }

    @Test
    public void testHttpRequestParsing() {
        {
            StatusPageServer.HttpRequest request = makeHttpRequest("/") ;
            assertEquals("/", request.getPath());
            assertFalse(request.hasQueryParameters());
        }
        {
            StatusPageServer.HttpRequest request = makeHttpRequest("/foo/bar");
            assertEquals("/foo/bar", request.getPath());
            assertFalse(request.hasQueryParameters());
        }
        {
            StatusPageServer.HttpRequest request = makeHttpRequest("/foo/bar?baz=baff");
            assertEquals("/foo/bar", request.getPath());
            assertTrue(request.hasQueryParameters());
            assertEquals("baff", request.getQueryParameter("baz"));
        }
        {
            StatusPageServer.HttpRequest request = makeHttpRequest("/?baz=baff&blarg=blee");
            assertEquals("/", request.getPath());
            assertTrue(request.hasQueryParameters());
            assertEquals("baff", request.getQueryParameter("baz"));
            assertEquals("blee", request.getQueryParameter("blarg"));
        }
        {
            StatusPageServer.HttpRequest request = makeHttpRequest("/node=storage.101?showlocal");
            assertEquals("/node=storage.101", request.getPath());
            assertTrue(request.hasQueryParameters());
            assertTrue(request.hasQueryParameter("showlocal"));
            assertNull(request.getQueryParameter("showlocal"));
        }
    }

    private static class DummyRequestHandler implements StatusPageServer.RequestHandler {
        private String returnData;
        public DummyRequestHandler(String returnData) {
            this.returnData = returnData;
        }

        @Override
        public StatusPageResponse handle(StatusPageServer.HttpRequest request) {
            StatusPageResponse response = new StatusPageResponse();
            response.writeContent(returnData);
            return response;
        }
    }

    private String invokeHandler(StatusPageServer.RequestRouter router, String request) {
        StatusPageServer.HttpRequest httpRequest = makeHttpRequest(request);
        StatusPageServer.RequestHandler handler = router.resolveHandler(httpRequest);
        if (handler == null) {
            return null;
        }
        try {
            return handler.handle(httpRequest).getOutputStream().toString("UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "<ERROR>";
        }
    }

    @Test
    public void testRequestRouting() {
        StatusPageServer.PatternRequestRouter router = new StatusPageServer.PatternRequestRouter();
        router.addHandler("^/alerts/red.*", new DummyRequestHandler("red alert!"));
        router.addHandler("^/alerts.*", new DummyRequestHandler("beige alert"));
        router.addHandler("^/$", new DummyRequestHandler("root"));
        assertEquals("root", invokeHandler(router, "/"));
        assertEquals("beige alert", invokeHandler(router, "/alerts"));
        assertEquals("beige alert", invokeHandler(router, "/alerts?foo"));
        assertEquals("red alert!", invokeHandler(router, "/alerts/red"));
        assertEquals("red alert!", invokeHandler(router, "/alerts/red/blue"));
        assertNull(invokeHandler(router, "/blarg"));
    }

    public String[] getResponseParts(String response) {
        int offset = response.indexOf("\r\n\r\n");
        if (offset == -1) {
            throw new IllegalStateException("No HTTP header delimiter found");
        }
        return new String[] {
                response.substring(0, offset + 2), // all header lines must have linebreaks
                response.substring(offset + 4)
        };
    }

    private String getHeaderValue(String header, String name) {
        int offset = header.indexOf(name + ": ");
        if (offset == -1) {
            throw new IllegalStateException("No HTTP header found for " + name);
        }
        int end = header.indexOf("\r\n", offset);
        if (end == -1) {
            throw new IllegalStateException("No EOL found for " + name);
        }
        return header.substring(offset + name.length() + 2, end);
    }

    @Test
    public void testStateServing() throws Exception {
        startingTest("StatusPagesTest::testStateServing()");
        FleetControllerOptions options = new FleetControllerOptions("mycluster");
        setUpFleetController(true, options);
        fleetController.updateOptions(options, 5);
        waitForCompleteCycle();
        {
            String content = doHttpGetRequest("/state/v1/health");
            String[] parts = getResponseParts(content);
            String body = parts[1];
            String expected =
                "{\n" +
                "  \"status\" : {\n" +
                "    \"code\" : \"up\"\n" +
                "  },\n" +
                "  \"config\" : {\n" +
                "    \"component\" : {\n" +
                "      \"generation\" : 5\n" +
                "    }\n" +
                "  }\n" +
                "}";
            assertEquals(expected, body);
            // Check that it actually parses
            JSONObject o = new JSONObject(expected);
        }
    }

    @Test
    public void testClusterStateServing() throws Exception {
        startingTest("StatusPagesTest::testClusterStateServing()");
        FleetControllerOptions options = new FleetControllerOptions("mycluster");
        setUpFleetController(true, options);
        fleetController.updateOptions(options, 5);
        waitForCompleteCycle();
        {
            String content = doHttpGetRequest("/clusterstate");
            String[] parts = getResponseParts(content);
            String body = parts[1];
            String expected = "version:2 cluster:d";
            assertEquals(expected, body);
        }
    }
}
