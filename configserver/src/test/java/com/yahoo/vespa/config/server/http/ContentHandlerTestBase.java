// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.OK;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public abstract class ContentHandlerTestBase extends SessionHandlerTest {
    protected String baseUrl = "http://foo:1337/application/v2/tenant/default/session/1/content/";

    @Test
    public void require_that_content_can_be_retrieved() throws IOException {
        assertContent("/test.txt", "foo\n");
        assertContent("/foo/", generateResultArray("foo/bar/", "foo/test1.json", "foo/test2.txt"), "application/json");
        assertContent("/foo", generateResultArray("foo/"), "application/json");
        assertContent("/foo/test1.json", "bar\n", "application/json");
        assertContent("/foo/test2.txt", "baz\n");
        assertContent("/foo/bar/", generateResultArray("foo/bar/file-without-extension", "foo/bar/test.jar"), "application/json");
        assertContent("/foo/bar", generateResultArray("foo/bar/"), "application/json");
        assertContent("/foo/bar/file-without-extension", "content");
        assertContent("/foo/bar/test.jar", "bim\n", "application/java-archive");
        assertContent("/foo/?recursive=true", generateResultArray("foo/bar/", "foo/bar/file-without-extension", "foo/bar/test.jar", "foo/test1.json", "foo/test2.txt"), "application/json");
    }

    @Test
    public void require_that_nonexistant_file_returns_not_found() {
        HttpResponse response = doRequest(HttpRequest.Method.GET, "/test2.txt");
        assertNotNull(response);
        assertEquals(NOT_FOUND, response.getStatus());
    }

    @Test
    public void require_that_return_property_is_used() throws IOException {
        assertContent("/test.txt?return=content", "foo\n");
    }

    @Test
    public void require_that_illegal_return_property_fails() {
        HttpResponse response = doRequest(HttpRequest.Method.GET, "/test.txt?return=foo");
        assertEquals(BAD_REQUEST, response.getStatus());
    }

    @Test
    public void require_that_status_can_be_retrieved() throws IOException {
        assertStatus("/test.txt?return=status",
                "{\"status\":\"new\",\"md5\":\"d3b07384d113edec49eaa6238ad5ff00\",\"name\":\"" + baseUrl + "test.txt\"}");
        assertStatus("/foo/?return=status",
                "[{\"status\":\"new\",\"md5\":\"\",\"name\":\"" + baseUrl + "foo/bar\"}," +
                        "{\"status\":\"new\",\"md5\":\"c157a79031e1c40f85931829bc5fc552\",\"name\":\"" + baseUrl + "foo/test1.json\"}," +
                        "{\"status\":\"new\",\"md5\":\"258622b1688250cb619f3c9ccaefb7eb\",\"name\":\"" + baseUrl + "foo/test2.txt\"}]");
        assertStatus("/foo/?return=status&recursive=true",
                "[{\"status\":\"new\",\"md5\":\"\",\"name\":\"" + baseUrl + "foo/bar\"}," +
                        "{\"status\":\"new\",\"md5\":\"9a0364b9e99bb480dd25e1f0284c8555\",\"name\":\"" + baseUrl + "foo/bar/file-without-extension\"}," +
                        "{\"status\":\"new\",\"md5\":\"579cae6111b269c0129af36a2243b873\",\"name\":\"" + baseUrl + "foo/bar/test.jar\"}," +
                        "{\"status\":\"new\",\"md5\":\"c157a79031e1c40f85931829bc5fc552\",\"name\":\"" + baseUrl + "foo/test1.json\"}," +
                        "{\"status\":\"new\",\"md5\":\"258622b1688250cb619f3c9ccaefb7eb\",\"name\":\"" + baseUrl + "foo/test2.txt\"}]");
    }

    protected void assertContent(String path, String expectedContent) throws IOException {
        assertContent(path, expectedContent, HttpResponse.DEFAULT_MIME_TYPE);
    }

    protected void assertContent(String path, String expectedContent, String expectedContentType) throws IOException {
        HttpResponse response = doRequest(HttpRequest.Method.GET, path);
        assertNotNull(response);
        final String renderedString = SessionHandlerTest.getRenderedString(response);
        assertEquals(renderedString, OK, response.getStatus());
        assertEquals(expectedContent, renderedString);
        assertEquals(expectedContentType, response.getContentType());
    }

    protected void assertStatus(String path, String expectedContent) throws IOException {
        HttpResponse response = doRequest(HttpRequest.Method.GET, path);
        assertNotNull(response);
        final String renderedString = SessionHandlerTest.getRenderedString(response);
        assertEquals(renderedString, OK, response.getStatus());
        assertEquals(expectedContent, renderedString);
    }

    protected abstract HttpResponse doRequest(HttpRequest.Method method, String path);

    private String generateResultArray(String... files) {
        Collection<String> output = Collections2.transform(Arrays.asList(files), input -> "\"" + baseUrl + input + "\"");
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append(Joiner.on(",").join(output));
        sb.append("]");
        return sb.toString();
    }
}
