// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application;

import com.yahoo.application.container.MockServer;
import com.yahoo.application.container.components.ComponentWithMetrics;
import com.yahoo.application.container.docprocs.MockDocproc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.application.container.handlers.MockHttpHandler;
import com.yahoo.application.container.renderers.MockRenderer;
import com.yahoo.application.container.searchers.MockSearcher;
import com.yahoo.component.Component;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.docproc.Processing;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.metrics.simple.Bucket;
import com.yahoo.metrics.simple.jdisc.SimpleMetricConsumer;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.handler.SearchHandler;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class ApplicationTest {

    @Test
    void minimal_application_can_be_constructed() {
        try (Application application = Application.fromServicesXml("<container version=\"1.0\"/>", Networking.disable)) {
            Application unused = application;
        }
    }

    /** Tests that an application with search chains referencing a content cluster can be constructed. */
    @Test
    void container_and_referenced_content() {
        try (Application application =
                Application.fromApplicationPackage(new File("src/test/app-packages/withcontent"), Networking.disable)) {
            Result result = application.getJDisc("default").search().process(new ComponentSpecification("default"),
                    new Query("?query=substring:foobar&timeout=20000"));
            assertEquals("WEAKAND(100) (AND substring:fo substring:oo substring:ob substring:ba substring:ar)",
                    result.hits().get("hasQuery").getQuery().getModel().getQueryTree().toString());
        }
    }

    @Test
    void application_with_query_profile_sets_up_query_profile_registry() {
        try (Application application =
                Application.fromApplicationPackage(new File("src/test/app-packages/withqueryprofile"), Networking.disable)) {
            Query query = new Query(HttpRequest.createTestRequest("?query=substring:foobar&timeout=20000", com.yahoo.jdisc.http.HttpRequest.Method.GET), application.getCompiledQueryProfileRegistry().findQueryProfile("default"));
            Result result = application.getJDisc("default").search().process(new ComponentSpecification("default"), query);

            assertEquals("WEAKAND(100) (AND substring:fo substring:oo substring:ob substring:ba substring:ar)",
                    result.hits().get("hasQuery").getQuery().getModel().getQueryTree().toString());
            assertEquals("2", application.getCompiledQueryProfileRegistry().findQueryProfile("default").get("hits"));
            assertEquals("select * from sources * where weakAnd(substring contains \"foobar\") limit 2 timeout 20000000", result.getQuery().yqlRepresentation(true));
        }
    }
    private void printTrace(Result result) {
        for (String message : result.getQuery().getContext(true).getTrace().traceNode().descendants(String.class))
            System.out.println(message);
    }

    @Test
    void empty_container() throws Exception {
        try (ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container())))) {
            try {
                app.process(new DocumentRemove(null));
                fail("expected exception");
            } catch (Exception ignore) {
                // no op
            }

            try {
                app.process(new Processing());
                fail("expected exception");
            } catch (Exception ignore) {
                // no op
            }

            try {
                app.search(new Query("?foo"));
                fail("expected exception");
            } catch (Exception ignore) {
                // no op
            }
        }
    }

    @Test
    void config() throws Exception {
        try (
                ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                        .documentProcessor("docproc", "default", MockDocproc.class)
                        .config(new MockApplicationConfig(new MockApplicationConfig.Builder()
                                .mystruct(new MockApplicationConfig.Mystruct.Builder().id("structid").value("structvalue"))
                                .mystructlist(new MockApplicationConfig.Mystructlist.Builder().id("listid1").value("listvalue1"))
                                .mystructlist(new MockApplicationConfig.Mystructlist.Builder().id("listid2").value("listvalue2"))
                                .mylist("item1")
                                .mylist("item2")
                                .mymap("key1", "value1")
                                .mymap("key2", "value2")
                                .mymapstruct("key1", new MockApplicationConfig.Mymapstruct.Builder().id("mapid1").value("mapvalue1"))
                                .mymapstruct("key2", new MockApplicationConfig.Mymapstruct.Builder().id("mapid2").value("mapvalue2")))))))
                ) {

            MockDocproc docproc = (MockDocproc) app.getComponentById("docproc@default");
            assertNotNull(docproc);

            // struct
            assertEquals(docproc.getConfig().mystruct().id(), "structid");
            assertEquals(docproc.getConfig().mystruct().value(), "structvalue");

            // struct list
            assertEquals(docproc.getConfig().mystructlist().size(), 2);
            assertEquals(docproc.getConfig().mystructlist().get(0).id(), "listid1");
            assertEquals(docproc.getConfig().mystructlist().get(0).value(), "listvalue1");
            assertEquals(docproc.getConfig().mystructlist().get(1).id(), "listid2");
            assertEquals(docproc.getConfig().mystructlist().get(1).value(), "listvalue2");

            // list
            assertEquals(docproc.getConfig().mylist().size(), 2);
            assertEquals(docproc.getConfig().mylist().get(0), "item1");
            assertEquals(docproc.getConfig().mylist().get(1), "item2");

            // map
            assertEquals(docproc.getConfig().mymap().size(), 2);
            assertTrue(docproc.getConfig().mymap().containsKey("key1"));
            assertEquals(docproc.getConfig().mymap().get("key1"), "value1");
            assertTrue(docproc.getConfig().mymap().containsKey("key2"));
            assertEquals(docproc.getConfig().mymap().get("key2"), "value2");

            // map struct
            assertEquals(docproc.getConfig().mymapstruct().size(), 2);
            assertTrue(docproc.getConfig().mymapstruct().containsKey("key1"));
            assertEquals(docproc.getConfig().mymapstruct().get("key1").id(), "mapid1");
            assertEquals(docproc.getConfig().mymapstruct().get("key1").value(), "mapvalue1");
            assertTrue(docproc.getConfig().mymapstruct().containsKey("key2"));
            assertEquals(docproc.getConfig().mymapstruct().get("key2").id(), "mapid2");
            assertEquals(docproc.getConfig().mymapstruct().get("key2").value(), "mapvalue2");
        }
    }

    @Test
    void handler() throws Exception {
        try (
                ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                        .handler("http://*/", MockHttpHandler.class))))
                ) {

            RequestHandler handler = app.getRequestHandlerById(MockHttpHandler.class.getName());
            assertNotNull(handler);

            Request request = new Request("http://localhost:" + getDefaults().vespaWebServicePort() + "/");
            Response response = app.handleRequest(request);
            assertNotNull(response);
            assertEquals(response.getStatus(), 200);
            assertEquals(response.getBodyAsString(), "OK");

            request = new Request("http://localhost");
            response = app.handleRequest(request);
            assertNotNull(response);
            assertEquals(response.getStatus(), 200);
            assertEquals(response.getBodyAsString(), "OK");

            request = new Request("http://localhost/?query=foo");
            response = app.handleRequest(request);
            assertNotNull(response);
            assertEquals(response.getStatus(), 200);
            assertEquals(response.getBodyAsString(), "OK");
        }
    }

    // TODO: Creates access log
    @Test
    void renderer() throws Exception {
        try (
                ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                        .renderer("mock", MockRenderer.class))))
                ) {

            Request request = new Request("http://localhost:" + getDefaults().vespaWebServicePort() + "/search/?format=mock");
            Response response = app.handleRequest(request);
            assertNotNull(response);
            assertEquals(response.getStatus(), 200);
            assertEquals(response.getBodyAsString(), "<mock hits=\"0\" />");
        }
    }

    @Test
    void search_default() throws Exception {
        try (
                ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                        .searcher(MockSearcher.class))))
                ) {
            Result result = app.search(new Query("?query=foo&timeout=20000"));
            assertEquals(1, result.hits().size());
        }
    }

    @Test
    void search() throws Exception {
        try (
                ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                        .searcher("foo", MockSearcher.class))))
                ) {
            Result result = app.search("foo", new Query("?query=foo&timeout=20000"));
            assertEquals(1, result.hits().size());
        }
    }

    @Test
    void document_type() throws Exception {
        try (
                Application app = Application.fromBuilder(new Application.Builder()
                        .documentType("test", new String(this.getClass().getResourceAsStream("/test.sd").readAllBytes(), StandardCharsets.UTF_8))
                        .container("default", new Application.Builder.Container()
                                .documentProcessor(MockDocproc.class)
                                .config(new MockApplicationConfig(new MockApplicationConfig.Builder().mystruct(new MockApplicationConfig.Mystruct.Builder().id("foo").value("bar"))))))
                ) {
            Map<String, DocumentType> typeMap = app.getJDisc("jdisc").documentProcessing().getDocumentTypes();
            assertNotNull(typeMap);
            assertTrue(typeMap.containsKey("test"));
        }
    }

    @Test
    void get_search_handler() throws Exception {
        try (ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container().search(true))))) {
            SearchHandler searchHandler = (SearchHandler) app.getRequestHandlerById("com.yahoo.search.handler.SearchHandler");
            assertNotNull(searchHandler);
        }
    }

    @Test
    void component() throws Exception {
        try (ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                .component(MockSearcher.class))))) {
            Component c = app.getComponentById(MockSearcher.class.getName());
            assertNotNull(c);
        }
    }

    @Test
    void application_generation_metric() throws Exception {
        try (ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                                                                                                                                          .component(ComponentWithMetrics.class))))) {
            var component = (ComponentWithMetrics)app.getComponentById(ComponentWithMetrics.class.getName());
            assertNotNull(component);
            var metrics = (SimpleMetricConsumer)component.metrics().newInstance(); // not actually a new instance
            assertNotNull(metrics);
            int maxWaitMs = 10000;
            Bucket snapshot = null;
            while (maxWaitMs-- > 0 && ( snapshot = metrics.receiver().getSnapshot() ) == null) {
                Thread.sleep(1);
            }
            assertNotNull(snapshot);
            assertEquals(1, snapshot.getValuesForMetric("application_generation").size());
            assertEquals(0, snapshot.getValuesForMetric("application_generation").iterator().next().getValue().getLast());
        }
    }

    @Test
    void component_with_config() throws Exception {
        MockApplicationConfig config = new MockApplicationConfig(new MockApplicationConfig.Builder().mystruct(new MockApplicationConfig.Mystruct.Builder().id("foo").value("bar")));
        try (ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                .component("foo", MockDocproc.class, config))))) {
            Component c = app.getComponentById("foo");
            assertNotNull(c);
        }
    }

    @Test
    void file_distribution() {
        try (Application application = Application.fromApplicationPackage(new File("src/test/app-packages/filedistribution/"), Networking.disable)) {
            // Deployment succeeded
            Application unused = application;
        }
    }

    @Test
    void server() throws Exception {
        try (ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                .server("foo", MockServer.class)))
        )) {
            MockServer server = (MockServer) app.getServerById("foo");
            assertNotNull(server);
            assertTrue(server.isStarted());
        }
    }

    @Test
    void query_profile() throws Exception {
        try (Application app = Application.fromBuilder(new Application.Builder()
                .queryProfile("default", "<query-profile id=\"default\">\n" +
                        "<field name=\"defaultage\">7d</field>\n" +
                        "</query-profile>")
                .queryProfileType("type", "<query-profile-type id=\"type\">\n" +
                        "<field name=\"defaultage\" type=\"string\" />\n" +
                        "</query-profile-type>")
                .rankExpression("re", "commonfirstphase(globalstaticrank)")
                .documentType("test", new String(this.getClass().getResourceAsStream("/test.sd").readAllBytes(), StandardCharsets.UTF_8))
                .container("default", new Application.Builder.Container()
                        .search(true)
                ))) {
            Application unused = app;
        }
    }

    @Test
    void http_interface_is_off_when_networking_is_disabled() throws Exception {
        assertThrows(ConnectException.class, () -> {
            int httpPort = getFreePort();
            try (Application application = Application.fromServicesXml(servicesXmlWithServer(httpPort), Networking.disable)) {
                HttpClient client = new org.apache.http.impl.client.DefaultHttpClient();
                int statusCode = client.execute(new HttpGet("http://localhost:" + httpPort)).getStatusLine().getStatusCode();
                fail("Networking.disable is specified, but the network interface is enabled! Got status code: " + statusCode);
                Application unused = application;
            }
        });
    }

    @Test
    void http_interface_is_on_when_networking_is_enabled() throws Exception {
        int httpPort = getFreePort();
        try (Application application = Application.fromServicesXml(servicesXmlWithServer(httpPort), Networking.enable)) {
            HttpClient client = new org.apache.http.impl.client.DefaultHttpClient();
            HttpResponse response = client.execute(new HttpGet("http://localhost:" + httpPort));
            assertEquals(200, response.getStatusLine().getStatusCode());
            BufferedReader r = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
            assertTrue(sb.toString().contains("Handler"));
            Application unused = application;
        }
    }

    @Test
    void athenz_in_deployment_xml() {
        try (Application application = Application.fromApplicationPackage(new File("src/test/app-packages/athenz-in-deployment-xml/"), Networking.disable)) {
            // Deployment succeeded
            Application unused = application;
        }
    }

    private static int getFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private static String servicesXmlWithServer(int port) {
        return "<container version='1.0'>" +
                "  <http> <server port='" + port +"' id='foo'/> </http>" +
               "  <accesslog type=\"disabled\" />" +
               "</container>";
    }

    @Test
    void application_with_access_control_can_be_constructed() {
        try (Application application = Application.fromServicesXml(servicesXmlWithAccessControl(), Networking.disable)) {
            Application unused = application;
        }
    }

    private static String servicesXmlWithAccessControl() {
        return "<container version='1.0'>" +
                "  <http> <server port='" + 0 +"' id='foo'/> " +
                "    <filtering>" +
                "      <access-control domain='foo' />" +
                "    </filtering>" +
                "  </http>" +
               "  <accesslog type=\"disabled\" />" +
               "</container>";
    }

}
