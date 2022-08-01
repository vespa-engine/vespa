// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container;

import com.yahoo.application.Application;
import com.yahoo.application.ApplicationBuilder;
import com.yahoo.application.Networking;
import com.yahoo.application.container.handler.Request;
import com.yahoo.application.container.handler.Response;
import com.yahoo.application.container.handlers.TestHandler;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.nio.charset.CharacterCodingException;
import java.nio.file.FileSystems;

import static com.yahoo.application.container.JDisc.fromServicesXml;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public class ContainerTest {

    @Test
    void container_can_be_used_as_top_level_element() {
        try (JDisc container = fromServicesXml("<container version=\"1.0\">" + //
                "<search />" + //
                "</container>", Networking.disable)) {
            assertNotNull(container.search());
        }
    }

    @Test
    void container_id_can_be_set() {
        try (JDisc container = fromServicesXml("<container version=\"1.0\" id=\"my-service-id\">" + //
                "<search />" + //
                "</container>", Networking.disable)) {
            assertNotNull(container.search());
        }
    }

    @Test
    void container_can_be_embedded_in_services_tag() {
        try (JDisc container = fromServicesXml("<services>" + //
                "<container version=\"1.0\" id=\"my-service-id\">" + //
                "<search />" + //
                "</container>" + //
                "</services>", Networking.disable)) {
            assertNotNull(container.search());
        }
    }

    // container is unused inside the try block
    @Test
    @SuppressWarnings("try")
    void multiple_container_elements_gives_exception() {
        try (JDisc container = fromServicesXml("<services>" + //
                "<container version=\"1.0\" id=\"id1\" />" + //
                "<container version=\"1.0\" />" + //
                "</services>", Networking.disable)) {
            fail("expected exception");
        } catch (Exception e) {
            assertTrue(e.getMessage().contains("container id='id1', container id=''"));
        }
    }

    @Test
    void handleRequest_yields_response_from_correct_request_handler() {
        final String handlerClass = TestHandler.class.getName();
        try (JDisc container = fromServicesXml("<container version=\"1.0\">" + //
                "<handler id=\"test-handler\" class=\"" + handlerClass + "\">" + //
                "<binding>http://*/TestHandler</binding>" + //
                "</handler>" + //
                "</container>", Networking.disable)) {
            Response response = container.handleRequest(new Request("http://foo/TestHandler"));
            try {
                assertEquals(TestHandler.RESPONSE, response.getBodyAsString());
            } catch (CharacterCodingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Test
    void load_searcher_from_bundle() {
        try (JDisc container = JDisc.fromPath(FileSystems.getDefault().getPath("src/test/app-packages/searcher-app"),
                Networking.disable)) {
            Result result = container.search().process(ComponentSpecification.fromString("default"),
                    new Query("?query=ignored"));
            assertEquals("Heal the World!", result.hits().get(0).getField("title").toString());
        }
    }

    @Test
    void document_types_can_be_accessed() throws Exception {
        try (Application application = new ApplicationBuilder().documentType("example", EXAMPLE_DOCUMENT)
                .servicesXml(CONTAINER_WITH_DOCUMENT_PROCESSING).build()) {
            JDisc container = application.getJDisc("container");
            DocumentProcessing processing = container.documentProcessing();
            assertTrue(processing.getDocumentTypes().containsKey("example"));
        }
    }

    @Test
    void annotation_types_can_be_accessed() throws Exception {
        try (Application application = new ApplicationBuilder().documentType("example", "search example {\n" + //
                "  " + EXAMPLE_DOCUMENT + "\n" + //
                "  annotation exampleAnnotation {}\n" + //
                "}\n").//
                servicesXml(CONTAINER_WITH_DOCUMENT_PROCESSING).build()) {
            JDisc container = application.getJDisc("container");
            DocumentProcessing processing = container.documentProcessing();
            assertTrue(processing.getAnnotationTypes().containsKey("exampleAnnotation"));
        }
    }

    @Disabled // Enable this when static state has been removed.
    @Test
    void multiple_containers_can_be_run_in_parallel() throws Exception {
        try (JDisc jdisc1 = jdiscWithHttp(); JDisc jdisc2 = jdiscWithHttp()) {
            sendRequest(jdisc1);
            sendRequest(jdisc2);
        }
    }

    private void sendRequest(JDisc jdisc) throws CharacterCodingException {
        Response response = jdisc.handleRequest(new Request("http://foo/TestHandler"));
        assertEquals(TestHandler.RESPONSE, response.getBodyAsString());
    }

    public static final String CONTAINER_WITH_DOCUMENT_PROCESSING = //
            "<container version=\"1.0\">" + //
                    "<http />" + //
                    "<document-processing />" + //
                    "</container>";

    public static final String EXAMPLE_DOCUMENT = //
            "document example {\n" + //
                    "\n" + //
                    "  field title type string {\n" + //
                    "    indexing: summary | index   # How this field should be indexed\n" + //
                    "    weight: 75 # Ranking importancy of this field, used by the built in nativeRank feature\n" + //
                    "  }\n" + //
                    "}\n";

    protected JDisc jdiscWithHttp() {
        final String handlerId = TestHandler.class.getName();
        final String xml = //
                "<container version=\"1.0\">" + //
                        "<handler id=" + handlerId + " />" + //
                        "<http>\n" + //
                        "<server id=\"main\" port=\"9999\" />\n" + //
                        "</http>\n" + //
                        "<accesslog type=\"disabled\" />" +
                        "</container>";
        return JDisc.fromServicesXml(xml, Networking.disable);
    }

}
