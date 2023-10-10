// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Base class for session handler tests
 *
 * @author hmusum
 */
public class SessionHandlerTest {

    protected String pathPrefix = "/application/v2/session/";
    public static final String hostname = "foo";
    public static final int port = 1337;


    public static HttpRequest createTestRequest(String path, com.yahoo.jdisc.http.HttpRequest.Method method,
                                                Cmd cmd, Long id, String subPath, InputStream data, Map<String, String> properties) {
        return HttpRequest.createTestRequest("http://" + hostname + ":" + port + path + "/" + id + "/" +
                                             cmd.toString() + subPath, method, data, properties);
    }

    public static HttpRequest createTestRequest(String path, com.yahoo.jdisc.http.HttpRequest.Method method,
                                                Cmd cmd, Long id, String subPath, InputStream data) {
        return HttpRequest.createTestRequest("http://" + hostname + ":" + port + path + "/" + id + "/" +
                                             cmd.toString() + subPath, method, data);
    }

    public static HttpRequest createTestRequest(String path, com.yahoo.jdisc.http.HttpRequest.Method method,
                                                Cmd cmd, Long id, String subPath) {
        return HttpRequest.createTestRequest("http://" + hostname + ":" + port + path + "/" + id + "/" +
                                             cmd.toString() + subPath, method);
    }

    public static HttpRequest createTestRequest(String path, com.yahoo.jdisc.http.HttpRequest.Method method,
                                                Cmd cmd, Long id) {
        return createTestRequest(path, method, cmd, id, "");
    }

    public static HttpRequest createTestRequest(String path) {
        return HttpRequest.createTestRequest("http://" + hostname + ":" + port + path, com.yahoo.jdisc.http.HttpRequest.Method.PUT);
    }

    public static String getRenderedString(HttpResponse response) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        return baos.toString(UTF_8);
    }

    public enum Cmd {
        PREPARED("prepared"),
        ACTIVE("active"),
        CONTENT("content");
        private final String name;

        Cmd(String s) {
            this.name = s;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
