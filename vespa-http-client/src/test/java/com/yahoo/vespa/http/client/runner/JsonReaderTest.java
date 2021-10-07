// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.runner;

import com.yahoo.vespa.http.client.FeedClient;
import com.yahoo.vespa.http.client.core.JsonReader;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.yahoo.vespa.http.client.JsonTestHelper.inputJson;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class JsonReaderTest {

    private static String doc1_id = "id:unittest:testMapStringToArrayOfInt::whee";

    private static String doc1 = inputJson(
            "{",
            "    'update': '"+ doc1_id + "',",
            "    'fields': {",
            "        'actualMapStringToArrayOfInt': {",
            "            'assign': [",
            "                { 'key': 'bamse', 'value': [ 2, 1, 3] }",
            "            ]",
            "        }",
            "    }",
            "}");

    private static String doc2_id = "id:unittest:smoke::whee";

    private static String doc2 = inputJson(
            "{",
            "    'put': '" + doc2_id + "',",
            "    'fields': {",
            "        'something': 'smoketest',",
            "        'nalle': 'bamse'",
            "    }",
            "}");

    private static String doc3 = inputJson(
            "{",
            "    'update': 'id:unittest:testarray::whee',",
            "    'fields': {",
            "        'actualarray': {",
            "            'add': [",
            "                'person naÃ¯ve',",
            "                'another person'",
            "            ]",
            "        }",
            "    }",
            "}");

    private static String doc4 = inputJson(
            "{",
            "    'remove': '" + doc2_id + "'",
            "}");

    private static String doc5_id = "id:unittest:smoking::wheels";

    private static String doc5 = inputJson(
            "{",
            "    'id': '" + doc5_id + "',",
            "    'fields': {",
            "        'something': 'smoketest',",
            "     'nalle': 'bamse'",
            "    }",
            "}");

    private static class TestFeedClient implements FeedClient {

        public List<String> documentIds = new ArrayList<>();
        public List<CharSequence> datas = new ArrayList<>();
        public List<Object> contexts = new ArrayList<>();

        @Override
        public void stream(String documentId, CharSequence documentData) {
            stream(documentId, documentData, null);
        }

        @Override
        public void stream(String documentId, String operationId, CharSequence documentData, Object context) {
            documentIds.add(documentId);
            datas.add(documentData);
            contexts.add(context);
        }

        @Override
        public void close() { }

        @Override
        public String getStatsAsJson() { return null; }
    }

    final TestFeedClient session = new TestFeedClient();
    final AtomicInteger numSent = new AtomicInteger(0);

    @Test
    public void testReadNoDocument() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                "  ".getBytes(StandardCharsets.UTF_8));
        JsonReader.read(inputStream, session, numSent);
        inputStream.close();
        assertThat(session.documentIds.size(), is(0));
    }

    @Test
    public void testReadOneDocument() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                ("["+ doc1 +  "]" ).getBytes(StandardCharsets.UTF_8));
        JsonReader.read(inputStream, session, numSent);
        inputStream.close();
        assertThat(session.documentIds.size(), is(1));
        assertThat(session.documentIds.get(0), is(doc1_id));
        assertThat(session.datas.size(), is(1));
        assertThat(session.datas.get(0), is(doc1));
    }

    @Test
    public void testReadFourDocuments() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                (" [ "+ doc1 + " ,  " + doc2  + ", " + doc3 + "," + doc4 + "  ] ").getBytes(StandardCharsets.UTF_8));
        JsonReader.read(inputStream, session, numSent);
        inputStream.close();
        assertThat(session.documentIds.size(), is(4));
        assertThat(session.documentIds.get(0), is(doc1_id));
        assertThat(session.documentIds.get(1), is(doc2_id));
        assertThat(session.datas.size(), is(4));
        assertThat(session.datas.get(0), is(doc1));
        assertThat(session.datas.get(1).toString(), is(doc2));
        assertThat(session.datas.get(2).toString(), is(doc3));
        assertThat(session.datas.get(3).toString(), is(doc4));
    }

    @Test
    public void testDocWithIdAndNotPut() throws Exception {
        InputStream inputStream = new ByteArrayInputStream(
                (" [ "+ doc5 + " ] ").getBytes(StandardCharsets.UTF_8));
        JsonReader.read(inputStream, session, numSent);
        inputStream.close();
        assertThat(session.documentIds.size(), is(1));
        assertThat(session.documentIds.get(0), is(doc5_id));
    }

    @Test
    public void simpleMicroBenchmarkTest() throws Exception {
        StringBuilder stream = new StringBuilder();
        stream.append("[");
        int docsInStream = 15000;
        for (int x = 0; x < docsInStream -1; x++) {
            if (x % 10 == 0) {
                stream.append(doc1 + ", ");
            } else {
                // Add some randomness to the layout to trigger potential bugs in parsing.
                stream.append("{\"remove\": \"id:unittest:smoke::whee");
                for (int y = 0 ; y < x % 277 ; y++) {
                    stream.append("X");
                }
                stream.append("\"}, ");
            }
        }
        stream.append(doc3);
        stream.append("]");

        InputStream inputStream = new ByteArrayInputStream(stream.toString().getBytes(StandardCharsets.UTF_8));
        long startTime = System.currentTimeMillis();
        JsonReader.read(inputStream, session, numSent);
        // At time of writing, it took about 200 ms on my mac.
        System.err.println("Run time is " + (System.currentTimeMillis() - startTime) + " ms");
        inputStream.close();

        // Verify that content is not rubbish.
        for (int x = 0; x < docsInStream - 1; x++) {
            if (x % 10 == 0) {
                assertThat(session.datas.get(x).toString(), is(doc1));
                assertThat(session.documentIds.get(x), is(doc1_id));
            }
        }
        assertThat(session.datas.get(docsInStream-1).toString(), is(doc3));
        assertThat(numSent.get(), is(docsInStream));
    }

    @Test(expected=RuntimeException.class)
    public void testBadJsonCommaAfterLastElement() {
        InputStream inputStream = new ByteArrayInputStream(
                ("["+ doc1 +  ",]" ).getBytes(StandardCharsets.UTF_8));
        JsonReader.read(inputStream, session, numSent);
    }

    @Test(expected=RuntimeException.class)
    public void testTotalGarbage() {
        InputStream inputStream = new ByteArrayInputStream(("garbage" ).getBytes(StandardCharsets.UTF_8));
        JsonReader.read(inputStream, session, numSent);
    }

    @Test(expected=RuntimeException.class)
    public void testTwoDocIds() {
        InputStream inputStream = new ByteArrayInputStream(("[{\"remove\": \"id\", \"update\": \"id:\"}]"
                .getBytes(StandardCharsets.UTF_8)));
        JsonReader.read(inputStream, session, numSent);
    }

    @Test(expected = IllegalArgumentException.class)
    public void throwsOnMissingId() {
        InputStream inputStream = new ByteArrayInputStream(
                inputJson("[{'fields':{ 'something': 'smoketest', 'nalle': 'bamse' }}]").getBytes(StandardCharsets.UTF_8));
        JsonReader.read(inputStream, session, numSent);
    }

    @Test
    public void testFullDocument() throws Exception {
        InputStream inputStream = new ByteArrayInputStream((
        "[{\n" +
                "        \"update\": \"id:foo:music:doc:foo:bar\",\n" +
                "        \n" +
                "        \"fields\": {\n" +
                "                \"artist\": {\n" +
                "                        \"assign\": null" +
                "                },\n" +
                "                \n" +
                "                \"albums\": {\n" +
                "                        \"assign\": [\n" +
                "                        \"Kramgoda laatar 4\",\n" +
                "                        \"Kramgoda laatar 5\",\n" +
                "                        \"Kramgoda laatar 6\"\n" +
                "                        ],\n" +
                "                        \"add\": [\n" +
                "                        \"Kramgoda laatar 7\",\n" +
                "                        \"Kramgoda laatar 8\"\n" +
                "                        ]\n" +
                "                },\n" +
                "                \"inceptionYear\": {\n" +
                "                        \"increment\": 4\n" +
                "                },\n" +
                "                \"concerts\": {\n" +
                "                        \"assign\": {\n" +
                "                                \"Torsby 1993\": 1000,\n" +
                "                                \"Uddevalla 2000\": 34\n" +
                "                        },\n" +
                "                        \"match\": {\n" +
                "                                \"element\": \"Sundsvall 1980\",\n" +
                "                                \"increment\": 5392\n" +
                "                        },\n" +
                "                        \"add\": {\n" +
                "                                \"Kiruna 1999\": 200,\n" +
                "                                \"Oslo 1998\": 2000\n" +
                "                        }\n" +
                "                },\n" +
                "                \"scores\": {\n" +
                "                        \"match\": {\n" +
                "                                \"element\": \"Sven Ingvars\",\n" +
                "                                \"match\": {\n" +
                "                                        \"element\": 0,\n" +
                "                                        \"increment\": 78\n" +
                "                                }\n" +
                "                        }\n" +
                "                }\n" +
                "        }\n" +
                "}]\n").getBytes(StandardCharsets.UTF_8));
        JsonReader.read(inputStream, session, numSent);
        inputStream.close();
        assertThat(session.documentIds.size(), is(1));
        assertThat(session.documentIds.get(0), is("id:foo:music:doc:foo:bar"));
    }
}
