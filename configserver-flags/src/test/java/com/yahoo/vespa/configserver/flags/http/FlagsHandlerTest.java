// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags.http;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.configserver.flags.FlagsDb;
import com.yahoo.vespa.configserver.flags.db.FlagsDbImpl;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.flags.FetchVector;
import com.yahoo.vespa.flags.FlagId;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.UnboundBooleanFlag;
import com.yahoo.yolean.Exceptions;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;

/**
 * @author hakonhall
 */
public class FlagsHandlerTest {
    private static final UnboundBooleanFlag FLAG1 = Flags.defineFeatureFlag(
            "id1", false, List.of("joe"), "2010-01-01", "2030-01-01", "desc1", "mod1");
    private static final UnboundBooleanFlag FLAG2 = Flags.defineFeatureFlag(
            "id2", true, List.of("joe"), "2010-01-01", "2030-01-01", "desc2", "mod2",
            FetchVector.Dimension.HOSTNAME, FetchVector.Dimension.APPLICATION_ID);

    private static final String FLAGS_V1_URL = "https://foo.com:4443/flags/v1";

    private final FlagsDb flagsDb = new FlagsDbImpl(new MockCurator());
    private final FlagsHandler handler = new FlagsHandler(FlagsHandler.testOnlyContext(), flagsDb);

    @Test
    public void testV1() {
        String expectedResponse = "{" +
                Stream.of("data", "defined")
                        .map(name -> "\"" + name + "\":{\"url\":\"https://foo.com:4443/flags/v1/" + name + "\"}")
                        .collect(Collectors.joining(",")) +
                "}";
        verifySuccessfulRequest(Method.GET, "", "", expectedResponse);
        verifySuccessfulRequest(Method.GET, "/", "", expectedResponse);
    }

    @Test
    public void testDefined() {
        try (Flags.Replacer replacer = Flags.clearFlagsForTesting()) {
            fixUnusedWarning(replacer);
            Flags.defineFeatureFlag("id", false, List.of("joe"), "2010-01-01", "2030-01-01", "desc", "mod", FetchVector.Dimension.HOSTNAME);
            verifySuccessfulRequest(Method.GET, "/defined", "",
                    "{\"id\":{\"description\":\"desc\",\"modification-effect\":\"mod\",\"owners\":[\"joe\"],\"createdAt\":\"2010-01-01T00:00:00Z\",\"expiresAt\":\"2030-01-01T00:00:00Z\",\"dimensions\":[\"hostname\"]}}");

            verifySuccessfulRequest(Method.GET, "/defined/id", "",
                    "{\"description\":\"desc\",\"modification-effect\":\"mod\",\"owners\":[\"joe\"],\"createdAt\":\"2010-01-01T00:00:00Z\",\"expiresAt\":\"2030-01-01T00:00:00Z\",\"dimensions\":[\"hostname\"]}");
        }
    }

    private void fixUnusedWarning(Flags.Replacer replacer) { }

    @Test
    public void testData() {
        // PUT flag with ID id1
        verifySuccessfulRequest(Method.PUT, "/data/" + FLAG1.id(),
                "{\n" +
                        "  \"id\": \"id1\",\n" +
                        "  \"rules\": [\n" +
                        "    {\n" +
                        "      \"value\": true\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}",
                "");

        // GET on ID id1 should return the same as the put.
        verifySuccessfulRequest(Method.GET, "/data/" + FLAG1.id(),
                "", "{\"id\":\"id1\",\"rules\":[{\"value\":true}]}");

        // List all flags should list only id1
        verifySuccessfulRequest(Method.GET, "/data",
                "", "{\"flags\":[{\"id\":\"id1\",\"url\":\"https://foo.com:4443/flags/v1/data/id1\"}]}");

        // Should be identical to above: suffix / on path should be ignored
        verifySuccessfulRequest(Method.GET, "/data/",
                "", "{\"flags\":[{\"id\":\"id1\",\"url\":\"https://foo.com:4443/flags/v1/data/id1\"}]}");

        // Verify absent port => absent in response
        assertThat(handleWithPort(Method.GET, -1, "/data", "", 200),
                is("{\"flags\":[{\"id\":\"id1\",\"url\":\"https://foo.com/flags/v1/data/id1\"}]}"));

        // PUT id2
        verifySuccessfulRequest(Method.PUT, "/data/" + FLAG2.id(),
                "{\n" +
                        "  \"id\": \"id2\",\n" +
                        "  \"rules\": [\n" +
                        "    {\n" +
                        "      \"conditions\": [\n" +
                        "        {\n" +
                        "          \"type\": \"whitelist\",\n" +
                        "          \"dimension\": \"hostname\",\n" +
                        "          \"values\": [ \"host1\", \"host2\" ]\n" +
                        "        },\n" +
                        "        {\n" +
                        "          \"type\": \"blacklist\",\n" +
                        "          \"dimension\": \"application\",\n" +
                        "          \"values\": [ \"app1\", \"app2\" ]\n" +
                        "        }\n" +
                        "      ],\n" +
                        "      \"value\": true\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"attributes\": {\n" +
                        "    \"zone\": \"zone1\"\n" +
                        "  }\n" +
                        "}\n",
                "");

        // GET on id2 should now return what was put
        verifySuccessfulRequest(Method.GET, "/data/" + FLAG2.id(), "",
                "{\"id\":\"id2\",\"rules\":[{\"conditions\":[{\"type\":\"whitelist\",\"dimension\":\"hostname\",\"values\":[\"host1\",\"host2\"]},{\"type\":\"blacklist\",\"dimension\":\"application\",\"values\":[\"app1\",\"app2\"]}],\"value\":true}],\"attributes\":{\"zone\":\"zone1\"}}");

        // The list of flag data should return id1 and id2
        verifySuccessfulRequest(Method.GET, "/data",
                "",
                "{\"flags\":[{\"id\":\"id1\",\"url\":\"https://foo.com:4443/flags/v1/data/id1\"},{\"id\":\"id2\",\"url\":\"https://foo.com:4443/flags/v1/data/id2\"}]}");

        // Putting (overriding) id1 should work silently
        verifySuccessfulRequest(Method.PUT, "/data/" + FLAG1.id(),
                "{\n" +
                        "  \"id\": \"id1\",\n" +
                        "  \"rules\": [\n" +
                        "    {\n" +
                        "      \"value\": false\n" +
                        "    }\n" +
                        "  ]\n" +
                        "}\n",
                "");

        // Verify PUT
        verifySuccessfulRequest(Method.GET, "/data/" + FLAG1.id(), "", "{\"id\":\"id1\",\"rules\":[{\"value\":false}]}");

        // Get all recursivelly displays all flag data
        verifySuccessfulRequest(Method.GET, "/data?recursive=true", "",
                "{\"flags\":[{\"id\":\"id1\",\"rules\":[{\"value\":false}]},{\"id\":\"id2\",\"rules\":[{\"conditions\":[{\"type\":\"whitelist\",\"dimension\":\"hostname\",\"values\":[\"host1\",\"host2\"]},{\"type\":\"blacklist\",\"dimension\":\"application\",\"values\":[\"app1\",\"app2\"]}],\"value\":true}],\"attributes\":{\"zone\":\"zone1\"}}]}");

        // Deleting both flags
        verifySuccessfulRequest(Method.DELETE, "/data/" + FLAG1.id(), "", "");
        verifySuccessfulRequest(Method.DELETE, "/data/" + FLAG2.id(), "", "");

        // And the list of data flags should now be empty
        verifySuccessfulRequest(Method.GET, "/data", "", "{\"flags\":[]}");
    }

    @Test
    public void testForcing() {
        assertThat(handle(Method.PUT, "/data/" + new FlagId("undef"), "", 400),
                containsString("There is no flag 'undef'"));

        assertThat(handle(Method.PUT, "/data/" + new FlagId("undef") + "?force=true", "", 400),
                containsString("No content to map due to end-of-input"));

        assertThat(handle(Method.PUT, "/data/" + FLAG1.id(), "{}", 400),
                containsString("Flag ID missing"));

        assertThat(handle(Method.PUT, "/data/" + FLAG1.id(), "{\"id\": \"id1\",\"rules\": [{\"value\":\"string\"}]}", 400),
                containsString("Wrong type of JsonNode: STRING"));

        assertThat(handle(Method.PUT, "/data/" + FLAG1.id() + "?force=true", "{\"id\": \"id1\",\"rules\": [{\"value\":\"string\"}]}", 200),
                is(""));
    }

    private void verifySuccessfulRequest(Method method, String pathSuffix, String requestBody, String expectedResponseBody) {
        assertThat(handle(method, pathSuffix, requestBody, 200), is(expectedResponseBody));
    }

    private String handle(Method method, String pathSuffix, String requestBody, int expectedStatus) {
        return handleWithPort(method, 4443, pathSuffix, requestBody, expectedStatus);
    }

    private String handleWithPort(Method method, int port, String pathSuffix, String requestBody, int expectedStatus) {
        String uri = "https://foo.com" + (port < 0 ? "" : ":" + port) + "/flags/v1" + pathSuffix;
        HttpRequest request = HttpRequest.createTestRequest(uri, method, makeInputStream(requestBody));
        HttpResponse response = handler.handle(request);
        assertEquals(expectedStatus, response.getStatus());
        assertEquals("application/json", response.getContentType());
        var outputStream = new ByteArrayOutputStream();
        Exceptions.uncheck(() -> response.render(outputStream));
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    private InputStream makeInputStream(String content) {
        return new ByteArrayInputStream(Utf8.toBytes(content));
    }
}
