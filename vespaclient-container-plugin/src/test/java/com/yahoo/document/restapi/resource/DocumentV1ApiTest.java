// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.docproc.jdisc.metric.NullMetric;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentGet;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.restapi.DocumentOperationExecutor;
import com.yahoo.document.restapi.DocumentOperationExecutor.Group;
import com.yahoo.document.restapi.DocumentOperationExecutor.VisitorOptions;
import com.yahoo.document.restapi.DocumentOperationExecutorMock;
import com.yahoo.document.restapi.resource.DocumentV1ApiHandler.DocumentOperationParser;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.documentapi.DocumentAccessParams;
import com.yahoo.documentapi.local.LocalDocumentAccess;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.prelude.fastsearch.StringField;
import com.yahoo.searchdefinition.derived.Deriver;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.test.ManualClock;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;

import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.BAD_REQUEST;
import static com.yahoo.document.restapi.DocumentOperationExecutor.ErrorType.ERROR;
import static com.yahoo.documentapi.DocumentOperationParameters.parameters;
import static com.yahoo.jdisc.http.HttpRequest.Method.DELETE;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static com.yahoo.jdisc.http.HttpRequest.Method.PUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class DocumentV1ApiTest {

    final DocumentmanagerConfig docConfig = Deriver.getDocumentManagerConfig("src/test/cfg/music.sd").build();
    final DocumentTypeManager manager = new DocumentTypeManager(docConfig);
    final Document doc1 = new Document(manager.getDocumentType("music"), "id:space:music::one");
    final Document doc2 = new Document(manager.getDocumentType("music"), "id:space:music:n=1:two");
    final Document doc3 = new Document(manager.getDocumentType("music"), "id:space:music:g=a:three");
    {
        doc1.setFieldValue("artist", "Tom Waits");
        doc2.setFieldValue("artist", "Asa-Chan & Jun-Ray");
    }

    ManualClock clock;
    DocumentOperationParser parser;
    LocalDocumentAccess access;
    DocumentOperationExecutorMock executor;
    Metric metric;
    MetricReceiver metrics;
    DocumentV1ApiHandler handler;

    @Before
    public void setUp() {
        clock = new ManualClock();
        parser = new DocumentOperationParser(docConfig);
        access = new LocalDocumentAccess(new DocumentAccessParams().setDocumentmanagerConfig(docConfig));
        executor = new DocumentOperationExecutorMock();
        metric = new NullMetric();
        metrics = new MetricReceiver.MockReceiver();
        handler = new DocumentV1ApiHandler(clock, executor, parser, metric, metrics);
    }

    @After
    public void tearDown() {
        handler.destroy();
    }

    @Test
    public void testResponses() {
        try (RequestHandlerTestDriver driver = new RequestHandlerTestDriver(handler)) {
            // GET at non-existent path returns 404 with available paths
            var response = driver.sendRequest("http://localhost/document/v1/not-found");
            assertSameJson("{" +
                           "  \"pathId\": \"/document/v1/not-found\"," +
                           "  \"message\": \"Nothing at '/document/v1/not-found'. Available paths are:\\n" +
                                                   "/document/v1/\\n" +
                                                   "/document/v1/{namespace}/{documentType}/docid/\\n" +
                                                   "/document/v1/{namespace}/{documentType}/group/{group}/\\n" +
                                                   "/document/v1/{namespace}/{documentType}/number/{number}/\\n" +
                                                   "/document/v1/{namespace}/{documentType}/docid/{docid}\\n" +
                                                   "/document/v1/{namespace}/{documentType}/group/{group}/{docid}\\n" +
                                                   "/document/v1/{namespace}/{documentType}/number/{number}/{docid}\"" +
                           "}",
                           response.readAll());
            assertEquals("application/json; charset=UTF-8", response.getResponse().headers().getFirst("Content-Type"));
            assertEquals(404, response.getStatus());

            // GET at root is a visit. Numeric parameters have an upper bound.
            response = driver.sendRequest("http://localhost/document/v1?cluster=lackluster&bucketSpace=default&wantedDocumentCount=1025&concurrency=123" +
                                          "&selection=all%20the%20things&fieldSet=[id]&continuation=token");
            executor.lastVisitContext().document(doc1);
            executor.lastVisitContext().document(doc2);
            executor.lastVisitContext().document(doc3);
            executor.lastVisitContext().success(Optional.of("token"));
            assertSameJson("{" +
                           "  \"pathId\": \"/document/v1\"," +
                           "  \"documents\": [" +
                           "    {" +
                           "      \"id\": \"id:space:music::one\"," +
                           "      \"fields\": {" +
                           "        \"artist\": \"Tom Waits\"" +
                           "      }" +
                           "    }," +
                           "    {" +
                           "      \"id\": \"id:space:music:n=1:two\"," +
                           "      \"fields\": {" +
                           "        \"artist\": \"Asa-Chan & Jun-Ray\"" +
                           "      }" +
                           "    }," +
                           "    {" +
                           "     \"id\": \"id:space:music:g=a:three\"," +
                           "     \"fields\": {}" +
                           "    }" +
                           "  ]," +
                           "  \"continuation\": \"token\"" +
                           "}",
                           response.readAll());
            assertEquals(200, response.getStatus());
            assertEquals(VisitorOptions.builder().cluster("lackluster").bucketSpace("default").wantedDocumentCount(1024)
                                       .concurrency(100).selection("all the things").fieldSet("[id]").continuation("token").build(),
                         executor.lastOptions());

            // GET with namespace and document type is a restricted visit.
            response = driver.sendRequest("http://localhost/document/v1/space/music/docid");
            executor.lastVisitContext().error(BAD_REQUEST, "nope");
            assertSameJson("{" +
                           "  \"pathId\": \"/document/v1/space/music/docid\"," +
                           "  \"documents\": []," +
                           "  \"message\": \"nope\"" +
                           "}",
                           response.readAll());
            assertEquals(400, response.getStatus());
            assertEquals(VisitorOptions.builder().namespace("space").documentType("music").build(),
                         executor.lastOptions());

            // GET with namespace, document type and group is a restricted visit.
            response = driver.sendRequest("http://localhost/document/v1/space/music/group/best");
            executor.lastVisitContext().error(ERROR, "error");
            assertSameJson("{" +
                           "  \"pathId\": \"/document/v1/space/music/group/best\"," +
                           "  \"documents\": []," +
                           "  \"message\": \"error\"" +
                           "}",
                           response.readAll());
            assertEquals(500, response.getStatus());
            assertEquals(VisitorOptions.builder().namespace("space").documentType("music").group(Group.of("best")).build(),
                         executor.lastOptions());

            // GET with namespace, document type and number is a restricted visit.
            response = driver.sendRequest("http://localhost/document/v1/space/music/number/123");
            executor.lastVisitContext().success(Optional.empty());
            assertSameJson("{" +
                           "  \"pathId\": \"/document/v1/space/music/number/123\"," +
                           "  \"documents\": []" +
                           "}",
                           response.readAll());
            assertEquals(200, response.getStatus());
            assertEquals(VisitorOptions.builder().namespace("space").documentType("music").group(Group.of(123)).build(),
                         executor.lastOptions());

            // GET with full document ID is a document get operation which returns 404 when no document is found
            response = driver.sendRequest("http://localhost/document/v1/space/music/docid/one?cluster=lackluster&fieldSet=go");
            executor.lastOperationContext().success(Optional.empty());
            assertSameJson("{" +
                           "  \"pathId\": \"/document/v1/space/music/docid/one\"," +
                           "  \"id\": \"id:space:music::one\"" +
                           "}",
                           response.readAll());
            assertEquals(404, response.getStatus());
            assertEquals(new DocumentGet(doc1.getId()), executor.lastOperation());
            assertEquals(parameters().withRoute("route-to-lackluster").withFieldSet("go"), executor.lastParameters());

            // GET with full document ID is a document get operation.
            response = driver.sendRequest("http://localhost/document/v1/space/music/docid/one?");
            executor.lastOperationContext().success(Optional.of(doc1));
            assertSameJson("{" +
                           "  \"pathId\": \"/document/v1/space/music/docid/one\"," +
                           "  \"id\": \"id:space:music::one\"," +
                           "  \"fields\": {" +
                           "    \"artist\": \"Tom Waits\"" +
                           "  }" +
                           "}",
                           response.readAll());
            assertEquals(200, response.getStatus());
            assertEquals(new DocumentGet(doc1.getId()), executor.lastOperation());
            assertEquals(parameters(), executor.lastParameters());

            // POST with a document payload is a document put operation.
            response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?condition=test%20it", POST,
                                          "{" +
                                          "  \"fields\": {" +
                                          "    \"artist\": \"Asa-Chan & Jun-Ray\"" +
                                          "  }" +
                                          "}");
            executor.lastOperationContext().success(Optional.empty());
            assertSameJson("{" +
                           "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                           "  \"id\": \"id:space:music:n=1:two\"" +
                           "}",
                           response.readAll());
            assertEquals(200, response.getStatus());
            DocumentPut put = new DocumentPut(doc2);
            put.setCondition(new TestAndSetCondition("test it"));
            assertEquals(put, executor.lastOperation());
            assertEquals(parameters(), executor.lastParameters());

            // PUT with a document update payload is a document update operation.
            response = driver.sendRequest("http://localhost/document/v1/space/music/group/a/three?create=true", PUT,
                                          "{" +
                                          "  \"fields\": {" +
                                          "    \"artist\": { \"assign\": \"Lisa Ekdahl\" }" +
                                          "  }" +
                                          "}");
            executor.lastOperationContext().success(Optional.empty());
            assertSameJson("{" +
                           "  \"pathId\": \"/document/v1/space/music/group/a/three\"," +
                           "  \"id\": \"id:space:music:g=a:three\"" +
                           "}",
                           response.readAll());
            assertEquals(200, response.getStatus());
            DocumentUpdate update = new DocumentUpdate(manager.getDocumentType("music"), "id:space:music:g=a:three");
            update.addFieldUpdate(FieldUpdate.createAssign(manager.getDocumentType("music").getField("artist"),
                                                           new StringFieldValue("Lisa Ekdahl")));
            update.setCreateIfNonExistent(true);
            assertEquals(update, executor.lastOperation());
            assertEquals(parameters(), executor.lastParameters());

            // DELETE with full document ID is a document remove operation.
            response = driver.sendRequest("http://localhost/document/v1/space/music/number/1/two?route=route", DELETE);
            executor.lastOperationContext().success(Optional.empty());
            assertSameJson("{" +
                           "  \"pathId\": \"/document/v1/space/music/number/1/two\"," +
                           "  \"id\": \"id:space:music:n=1:two\"" +
                           "}",
                           response.readAll());
            assertEquals(200, response.getStatus());
            assertEquals(new DocumentRemove(doc2.getId()), executor.lastOperation());
            assertEquals(parameters().withRoute("route"), executor.lastParameters());
        }
    }

    void assertSameJson(String expected, String actual) {
        ByteArrayOutputStream expectedPretty = new ByteArrayOutputStream();
        ByteArrayOutputStream actualPretty = new ByteArrayOutputStream();
        JsonFormat formatter = new JsonFormat(false);
        try {
            formatter.encode(actualPretty, SlimeUtils.jsonToSlimeOrThrow(actual));
            formatter.encode(expectedPretty, SlimeUtils.jsonToSlimeOrThrow(expected));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(expectedPretty.toString(UTF_8), actualPretty.toString(UTF_8));
    }

}
