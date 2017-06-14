// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.OK;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.Nullable;

import org.junit.Test;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest;

public abstract class ContentHandlerTestBase extends SessionHandlerTest {
    protected String baseUrl = "http://foo:1337/application/v2/tenant/default/session/1/content/";

    @Test
    public void require_that_content_can_be_retrieved() throws IOException {
        assertContent("/test.txt", "foo\n");
        assertContent("/foo/", generateResultArray("foo/bar/", "foo/test1.txt", "foo/test2.txt"));
        assertContent("/foo", generateResultArray("foo/"));
        assertContent("/foo/test1.txt", "bar\n");
        assertContent("/foo/test2.txt", "baz\n");
        assertContent("/foo/bar/", generateResultArray("foo/bar/test.txt"));
        assertContent("/foo/bar", generateResultArray("foo/bar/"));
        assertContent("/foo/bar/test.txt", "bim\n");
        assertContent("/foo/?recursive=true", generateResultArray("foo/bar/", "foo/bar/test.txt", "foo/test1.txt", "foo/test2.txt"));
    }

    @Test
    public void require_that_nonexistant_file_returns_not_found() throws IOException {
        HttpResponse response = doRequest(HttpRequest.Method.GET, "/test2.txt");
        assertNotNull(response);
        assertThat(response.getStatus(), is(NOT_FOUND));
    }

    @Test
    public void require_that_return_property_is_used() throws IOException {
        assertContent("/test.txt?return=content", "foo\n");
    }

    @Test
    public void require_that_illegal_return_property_fails() {
        HttpResponse response = doRequest(HttpRequest.Method.GET, "/test.txt?return=foo");
        assertThat(response.getStatus(), is(BAD_REQUEST));
    }

    @Test
    public void require_that_status_can_be_retrieved() throws IOException {
        assertStatus("/test.txt?return=status",
                "{\"status\":\"new\",\"md5\":\"d3b07384d113edec49eaa6238ad5ff00\",\"name\":\"" + baseUrl + "test.txt\"}");
        assertStatus("/foo/?return=status",
                "[{\"status\":\"new\",\"md5\":\"\",\"name\":\"" + baseUrl + "foo/bar\"}," +
                        "{\"status\":\"new\",\"md5\":\"c157a79031e1c40f85931829bc5fc552\",\"name\":\"" + baseUrl + "foo/test1.txt\"}," +
                        "{\"status\":\"new\",\"md5\":\"258622b1688250cb619f3c9ccaefb7eb\",\"name\":\"" + baseUrl + "foo/test2.txt\"}]");
        assertStatus("/foo/?return=status&recursive=true",
                "[{\"status\":\"new\",\"md5\":\"\",\"name\":\"" + baseUrl + "foo/bar\"}," +
                        "{\"status\":\"new\",\"md5\":\"579cae6111b269c0129af36a2243b873\",\"name\":\"" + baseUrl + "foo/bar/test.txt\"}," +
                        "{\"status\":\"new\",\"md5\":\"c157a79031e1c40f85931829bc5fc552\",\"name\":\"" + baseUrl + "foo/test1.txt\"}," +
                        "{\"status\":\"new\",\"md5\":\"258622b1688250cb619f3c9ccaefb7eb\",\"name\":\"" + baseUrl + "foo/test2.txt\"}]");
    }

    protected void assertContent(String path, String expectedContent) throws IOException {
        HttpResponse response = doRequest(HttpRequest.Method.GET, path);
        assertNotNull(response);
        final String renderedString = SessionHandlerTest.getRenderedString(response);
        assertThat(renderedString, response.getStatus(), is(OK));
        assertThat(renderedString, is(expectedContent));
    }

    protected void assertStatus(String path, String expectedContent) throws IOException {
        HttpResponse response = doRequest(HttpRequest.Method.GET, path);
        assertNotNull(response);
        final String renderedString = SessionHandlerTest.getRenderedString(response);
        assertThat(renderedString, response.getStatus(), is(OK));
        assertThat(renderedString, is(expectedContent));
    }

    protected abstract HttpResponse doRequest(HttpRequest.Method method, String path);

    private String generateResultArray(String... files) {
        Collection<String> output = Collections2.transform(Arrays.asList(files), new Function<String, String>() {
            @Override
            public String apply(@Nullable String input) {
                return "\"" + baseUrl + input + "\"";
            }
        });
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        sb.append(Joiner.on(",").join(output));
        sb.append("]");
        return sb.toString();
    }
}
