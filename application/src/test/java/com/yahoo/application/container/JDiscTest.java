// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.application.Application;
import com.yahoo.application.ApplicationBuilder;
import com.yahoo.application.Networking;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.application.container.handlers.TestHandler;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.Container;
import com.yahoo.jdisc.http.server.jetty.JettyHttpServer;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import org.junit.Ignore;
import org.junit.Test;

import java.nio.charset.CharacterCodingException;
import java.nio.file.FileSystems;

import static com.yahoo.application.container.JDisc.fromServicesXml;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ovirtanen
 */
public class JDiscTest {
    @Test
    public void jdisc_can_be_used_as_top_level_element() throws Exception {
        try (JDisc container = fromServicesXml("<jdisc version=\"1.0\">" + //
                "<search />" + //
                "</jdisc>", Networking.disable)) {
            assertNotNull(container.search());
        }
    }

    @Test
    public void jdisc_id_can_be_set() throws Exception {
        try (JDisc container = fromServicesXml("<jdisc version=\"1.0\" id=\"my-service-id\">" + //
                "<search />" + //
                "</jdisc>", Networking.disable)) {
            assertNotNull(container.search());
        }
    }

    @Test
    public void jdisc_can_be_embedded_in_services_tag() throws Exception {
        try (JDisc container = fromServicesXml("<services>" + //
                "<jdisc version=\"1.0\" id=\"my-service-id\">" + //
                "<search />" + //
                "</jdisc>" + //
                "</services>", Networking.disable)) {
            assertNotNull(container.search());
        }
    }

    @Test
    @SuppressWarnings("try") // container is unused inside the try block
    public void multiple_jdisc_elements_gives_exception() {
        try (JDisc container = fromServicesXml("<services>" + //
                "<jdisc version=\"1.0\" id=\"id1\" />" + //
                "<jdisc version=\"1.0\" />" + //
                "<container version=\"1.0\"/>" + //
                "</services>", Networking.disable)) {
            fail("expected exception");
        } catch (Exception e) {
            assertThat(e.getMessage(), containsString("container id='', jdisc id='id1', jdisc id=''"));
        }
    }

    @Test
    public void handleRequest_yields_response_from_correct_request_handler() throws Exception {
        final String handlerClass = TestHandler.class.getName();
        try (JDisc container = fromServicesXml("<container version=\"1.0\">" + //
                "<handler id=\"test-handler\" class=\"" + handlerClass + "\">" + //
                "<binding>http://*/TestHandler</binding>" + //
                "</handler>" + //
                "</container>", Networking.disable)) {
            Response response = container.handleRequest(new Request("http://foo/TestHandler"));
            try {
                assertThat(response.getBodyAsString(), is(TestHandler.RESPONSE));
            } catch (CharacterCodingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    public void load_searcher_from_bundle() throws Exception {
        try (JDisc container = JDisc.fromPath(FileSystems.getDefault().getPath("src/test/app-packages/searcher-app"),
                Networking.disable)) {
            Result result = container.search().process(ComponentSpecification.fromString("default"),
                    new Query("?query=ignored"));
            assertThat(result.hits().get(0).getField("title").toString(), is("Heal the World!"));
        }
    }

    @Test
    public void document_types_can_be_accessed() throws Exception {
        try (Application application = new ApplicationBuilder().documentType("example", EXAMPLE_DOCUMENT)
                .servicesXml(CONTAINER_WITH_DOCUMENT_PROCESSING).build()) {
            JDisc container = application.getJDisc("jdisc");
            DocumentProcessing processing = container.documentProcessing();
            assertThat(processing.getDocumentTypes().keySet(), hasItem("example"));
        }
    }

    @Test
    public void annotation_types_can_be_accessed() throws Exception {
        try (Application application = new ApplicationBuilder().documentType("example", "search example {\n" + //
                "  " + EXAMPLE_DOCUMENT + "\n" + //
                "  annotation exampleAnnotation {}\n" + //
                "}\n").//
                servicesXml(CONTAINER_WITH_DOCUMENT_PROCESSING).build()) {
            JDisc container = application.getJDisc("jdisc");
            DocumentProcessing processing = container.documentProcessing();
            assertThat(processing.getAnnotationTypes().keySet(), hasItem("exampleAnnotation"));
        }
    }

    @Ignore // Enable this when static state has been removed.
    @Test
    public void multiple_containers_can_be_run_in_parallel() throws Exception {
        try (JDisc jdisc1 = jdiscWithHttp(); JDisc jdisc2 = jdiscWithHttp()) {
            sendRequest(jdisc1);
            sendRequest(jdisc2);
        }
    }

    private void sendRequest(JDisc jdisc) throws CharacterCodingException {
        Response response = jdisc.handleRequest(new Request("http://foo/TestHandler"));
        assertThat(response.getBodyAsString(), is(TestHandler.RESPONSE));
    }

    public static final String CONTAINER_WITH_DOCUMENT_PROCESSING = //
            "<jdisc version=\"1.0\">" + //
                    "<http />" + //
                    "<document-processing />" + //
                    "</jdisc>";

    public static final String EXAMPLE_DOCUMENT = //
            "document example {\n" + //
                    "\n" + //
                    "  field title type string {\n" + //
                    "    indexing: summary | index   # How this field should be indexed\n" + //
                    "    weight: 75 # Ranking importancy of this field, used by the built in nativeRank feature\n" + //
                    "    header\n" + //
                    "  }\n" + //
                    "}\n";

    protected JDisc jdiscWithHttp() {
        final String handlerId = TestHandler.class.getName();
        final String xml = //
                "<jdisc version=\"1.0\">" + //
                        "<handler id=" + handlerId + " />" + //
                        "<http>\n" + //
                        "<server id=\"main\" port=\"9999\" />\n" + //
                        "</http>\n" + //
                        "</jdisc>";
        return JDisc.fromServicesXml(xml, Networking.disable);
    }

    public static int getListenPort() {
        for (ServerProvider server : Container.get().getServerProviderRegistry().allComponents()) {
            if (null != server && server instanceof JettyHttpServer) {
                return ((JettyHttpServer) server).getListenPort();
            }
        }
        throw new RuntimeException("No http server found");
    }
}
