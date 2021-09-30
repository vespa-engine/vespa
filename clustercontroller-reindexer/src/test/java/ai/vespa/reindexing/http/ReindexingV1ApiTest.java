// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.reindexing.http;

import ai.vespa.reindexing.Reindexing;
import ai.vespa.reindexing.Reindexing.Status;
import ai.vespa.reindexing.ReindexingCurator;
import com.yahoo.container.jdisc.RequestHandlerTestDriver;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.documentapi.ProgressToken;
import com.yahoo.jdisc.test.MockMetric;
import com.yahoo.searchdefinition.derived.Deriver;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
class ReindexingV1ApiTest {

    DocumentmanagerConfig musicConfig = Deriver.getDocumentManagerConfig("src/test/resources/schemas/music.sd").build();
    DocumentTypeManager manager = new DocumentTypeManager(musicConfig);
    DocumentType musicType = manager.getDocumentType("music");
    ReindexingCurator database = new ReindexingCurator(new MockCurator(), manager);
    ReindexingV1ApiHandler handler = new ReindexingV1ApiHandler(Executors.newSingleThreadExecutor(), new MockMetric(),
                                                                List.of("cluster", "oyster"), database);

    @Test
    void testResponses() {
        RequestHandlerTestDriver driver = new RequestHandlerTestDriver(handler);

        // GET at root
        var response = driver.sendRequest("http://localhost/reindexing/v1/");
        assertEquals("{\"resources\":[{\"url\":\"/reindexing/v1/status\"}]}", response.readAll());
        assertEquals("application/json; charset=UTF-8", response.getResponse().headers().getFirst("Content-Type"));
        assertEquals(200, response.getStatus());

        // GET at status with empty database
        response = driver.sendRequest("http://localhost/reindexing/v1/status");
        assertEquals("{\"clusters\":{\"cluster\":{\"documentTypes\":{}},\"oyster\":{\"documentTypes\":{}}}}", response.readAll());
        assertEquals(200, response.getStatus());

        // GET at status with a failed status
        database.writeReindexing(Reindexing.empty().with(musicType, Status.ready(Instant.EPOCH)
                                                                          .running()
                                                                          .progressed(new ProgressToken())
                                                                          .failed(Instant.ofEpochMilli(123), "ヽ(。_°)ノ")),
                                 "cluster");
        response = driver.sendRequest("http://localhost/reindexing/v1/status");
        assertEquals("{" +
                     "\"clusters\":{" +
                       "\"cluster\":{" +
                         "\"documentTypes\":{" +
                           "\"music\":{" +
                             "\"startedMillis\":0," +
                             "\"endedMillis\":123," +
                             "\"progress\":1.0," +
                             "\"state\":\"failed\"," +
                             "\"message\":\"ヽ(。_°)ノ\"}" +
                           "}" +
                         "}," +
                         "\"oyster\":{" +
                           "\"documentTypes\":{}" +
                         "}" +
                       "}" +
                     "}",
                     response.readAll());
        assertEquals(200, response.getStatus());

        // POST at root
        response = driver.sendRequest("http://localhost/reindexing/v1/status", POST);
        assertEquals("{\"error-code\":\"METHOD_NOT_ALLOWED\",\"message\":\"Only GET is supported under /reindexing/v1/\"}",
                     response.readAll());
        assertEquals(405, response.getStatus());

        // GET at non-existent path
        response = driver.sendRequest("http://localhost/reindexing/v1/moo");
        assertEquals("{\"error-code\":\"NOT_FOUND\",\"message\":\"Nothing at /reindexing/v1/moo\"}",
                     response.readAll());
        assertEquals(404, response.getStatus());

    }

}
