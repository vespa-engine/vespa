// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application;

import com.yahoo.application.container.MockClient;
import com.yahoo.application.container.MockServer;
import com.yahoo.application.container.docprocs.MockDispatchDocproc;
import com.yahoo.application.container.docprocs.MockDocproc;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.application.container.handlers.MockHttpHandler;
import com.yahoo.application.container.renderers.MockRenderer;
import com.yahoo.application.container.searchers.MockSearcher;
import com.yahoo.component.Component;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.handler.SearchHandler;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import static com.yahoo.application.container.JDiscTest.getListenPort;

/**
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class ApplicationTest {

    @Test
    public void minimal_application_can_be_constructed() throws Exception {
        try (Application application = Application.fromServicesXml("<jdisc version=\"1.0\"/>", Networking.disable)) {
                Application unused = application;
        }
    }

    /** Tests that an application with search chains referencing a content cluster can be constructed. */
    @Test
    public void container_and_referenced_content() throws Exception {
        try (Application application =
                     Application.fromApplicationPackage(new File("src/test/app-packages/withcontent"), Networking.disable)) {
            Result result = application.getJDisc("default").search().process(new ComponentSpecification("default"),
                                                                             new Query("?query=substring:foobar&tracelevel=3"));
            assertEquals("AND substring:fo substring:oo substring:ob substring:ba substring:ar", result.hits().get("hasQuery").getQuery().getModel().getQueryTree().toString());
        }
    }

    private void printTrace(Result result) {
        for (String message : result.getQuery().getContext(true).getTrace().traceNode().descendants(String.class))
            System.out.println(message);
    }

    @Test
    public void empty_container() throws Exception {
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
    public void config() throws Exception {
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
    public void handler() throws Exception {
        try (
                ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                        .handler("http://*/*", MockHttpHandler.class))))
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

            request = new Request("http://localhost/query=foo");
            response = app.handleRequest(request);
            assertNotNull(response);
            assertEquals(response.getStatus(), 200);
            assertEquals(response.getBodyAsString(), "OK");
        }
    }

    @Test
    public void renderer() throws Exception {
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
    public void search_default() throws Exception {
        try (
                ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                        .searcher(MockSearcher.class))))
        ) {
            Result result = app.search(new Query("?query=foo"));
            assertEquals(1, result.hits().size());
        }
    }

    @Test
    public void search() throws Exception {
        try (
                ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                        .searcher("foo", MockSearcher.class))))
        ) {
            Result result = app.search("foo", new Query("?query=foo"));
            assertEquals(1, result.hits().size());
        }
    }

    @Test
    public void document_type() throws Exception {
        try (
                Application app = Application.fromBuilder(new Application.Builder()
                        .documentType("test", IOUtils.toString(this.getClass().getResourceAsStream("/test.sd")))
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
    public void get_search_handler() throws Exception {
        try (ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container().search(true))))) {
            SearchHandler searchHandler = (SearchHandler) app.getRequestHandlerById("com.yahoo.search.handler.SearchHandler");
            assertNotNull(searchHandler);
        }
    }

    @Test
    public void component() throws Exception {
        try (ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                .component(MockSearcher.class))))) {
            Component c = app.getComponentById(MockSearcher.class.getName());
            assertNotNull(c);
        }
    }

    @Test
    public void component_with_config() throws Exception {
        MockApplicationConfig config = new MockApplicationConfig(new MockApplicationConfig.Builder().mystruct(new MockApplicationConfig.Mystruct.Builder().id("foo").value("bar")));
        try (ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                .component("foo", MockDocproc.class, config))))) {
            Component c = app.getComponentById("foo");
            assertNotNull(c);
        }
    }

    @Test
    public void client() throws Exception {
        try (ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder()
                .documentType("test", IOUtils.toString(this.getClass().getResourceAsStream("/test.sd")))
                .container("default", new Application.Builder.Container()
                    .client("mbus://*/*", MockClient.class)
                    .documentProcessor(MockDispatchDocproc.class)
                ))
        )) {

            Map<String, DocumentType> typeMap = app.application().getJDisc("jdisc").documentProcessing().getDocumentTypes();
            assertNotNull(typeMap);

            DocumentType docType = typeMap.get("test");
            Document doc = new Document(docType, "id:foo:test::bar");
            doc.setFieldValue("title", "hello");

            assertEquals(DocumentProcessor.Progress.DONE, app.process(new DocumentPut(doc)));

            MockClient client = (MockClient) app.getClientById(MockClient.class.getName());
            assertNotNull(client);
            assertEquals(1, client.getCounter());

            MockDispatchDocproc docproc = (MockDispatchDocproc) app.getComponentById(MockDispatchDocproc.class.getName() + "@default");
            assertNotNull(docproc);
            assertEquals(1, docproc.getResponses().size());
            assertEquals(200, docproc.getResponses().get(0).getStatus());
        }
    }
    
    @Test
    public void file_distribution() throws Exception {
        try (Application application = Application.fromApplicationPackage(new File("src/test/app-packages/filedistribution/"), Networking.disable)) {
            // Deployment succeeded
            Application unused = application;
        }
    }

    @Test
    public void server() throws Exception {
        try (ApplicationFacade app = new ApplicationFacade(Application.fromBuilder(new Application.Builder().container("default", new Application.Builder.Container()
                .server("foo", MockServer.class)))
        )) {
            MockServer server = (MockServer) app.getServerById("foo");
            assertNotNull(server);
            assertTrue(server.isStarted());
        }
    }

    @Test
    public void query_profile() throws Exception {
        try (Application app = Application.fromBuilder(new Application.Builder()
                .queryProfile("default", "<query-profile id=\"default\">\n" +
                        "<field name=\"defaultage\">7d</field>\n" +
                        "</query-profile>")
                .queryProfileType("type", "<query-profile-type id=\"type\">\n" +
                        "<field name=\"defaultage\" type=\"string\" />\n" +
                        "</query-profile-type>")
                .rankExpression("re", "commonfirstphase(globalstaticrank)")
                .documentType("test", IOUtils.toString(this.getClass().getResourceAsStream("/test.sd")))
                .container("default", new Application.Builder.Container()
                .search(true)
                ))) {
            Application unused = app;
        }
    }

    @Test(expected = ConnectException.class)
    public void http_interface_is_off_when_networking_is_disabled() throws Exception {
        int httpPort = getFreePort();
        try (Application application = Application.fromServicesXml(servicesXmlWithServer(httpPort), Networking.disable)) {
            HttpClient client = new org.apache.http.impl.client.DefaultHttpClient();
            int statusCode = client.execute(new HttpGet("http://localhost:" + httpPort)).getStatusLine().getStatusCode();
            fail("Networking.disable is specified, but the network interface is enabled! Got status code: " + statusCode);
            Application unused = application;
        }
    }

    @Test
    public void http_interface_is_on_when_networking_is_enabled() throws Exception {
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

    private static int getFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    private static String servicesXmlWithServer(int port) {
        return "<jdisc version='1.0'>" +
                "  <http> <server port='" + port +"' id='foo'/> </http>" +
                "</jdisc>";
    }

    @Test
    public void application_with_access_control_can_be_constructed() throws Exception {
        try (Application application = Application.fromServicesXml(servicesXmlWithAccessControl(), Networking.disable)) {
            Application unused = application;
        }
    }

    private static String servicesXmlWithAccessControl() {
        return "<jdisc version='1.0'>" +
                "  <http> <server port='" + 0 +"' id='foo'/> " +
                "    <filtering>" +
                "      <access-control domain='foo' />" +
                "    </filtering>" +
                "  </http>" +
                "</jdisc>";
    }

}
