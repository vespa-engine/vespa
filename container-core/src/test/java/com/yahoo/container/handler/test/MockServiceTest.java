// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.test;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Ulf Lilleengen
 */
public class MockServiceTest {

    private final File testFile = new File("src/test/java/com/yahoo/container/handler/test/test.txt");

    @Test
    void testHandlerTextFormat() throws InterruptedException, IOException {
        HttpResponse response = runHandler(com.yahoo.jdisc.http.HttpRequest.Method.GET, "/foo/bar");
        assertResponse(response, 200, "Hello\nThere!");

        response = runHandler(com.yahoo.jdisc.http.HttpRequest.Method.GET, "http://my.host:8080/foo/bar?key1=foo&key2=bar");
        assertResponse(response, 200, "With params!");

        response = runHandler(com.yahoo.jdisc.http.HttpRequest.Method.PUT, "/bar");
        assertResponse(response, 301, "My data is on a single line");
    }

    @Test
    void testNoHandlerFound() throws InterruptedException, IOException {
        HttpResponse response = runHandler(com.yahoo.jdisc.http.HttpRequest.Method.DELETE, "/foo/bar");
        assertEquals(404, response.getStatus());
        assertResponseContents(response, "DELETE:/foo/bar was not found");
    }

    @Test
    void testUnknownFileType() throws InterruptedException, IOException {
        assertThrows(IllegalArgumentException.class, () -> {
            runHandlerWithFile(com.yahoo.jdisc.http.HttpRequest.Method.GET, "", new File("nonexistant"));
        });
    }

    @Test
    void testExceptionResponse() throws InterruptedException, IOException {
        assertThrows(FileNotFoundException.class, () -> {
            runHandlerWithFile(com.yahoo.jdisc.http.HttpRequest.Method.GET, "", new File("nonexistant.txt"));
        });
    }

    private void assertResponse(HttpResponse response, int expectedCode, String expectedMessage) throws IOException {
        assertEquals(expectedCode, response.getStatus());
        assertResponseContents(response, expectedMessage);
    }

    private void assertResponseContents(HttpResponse response, String expected) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        assertEquals(expected, baos.toString());
    }

    private HttpResponse runHandler(com.yahoo.jdisc.http.HttpRequest.Method method, String path) throws InterruptedException, IOException {
        return runHandlerWithFile(method, path, testFile);
    }

    private HttpResponse runHandlerWithFile(com.yahoo.jdisc.http.HttpRequest.Method method, String path, File file) throws InterruptedException, IOException {
        MockserviceConfig.Builder builder = new MockserviceConfig.Builder();
        builder.file(file.getPath());
        MockService handler = new MockService(new MockExecutor(), MockFileAcquirer.returnFile(file), new MockserviceConfig(builder), null);
        return handler.handle(HttpRequest.createTestRequest(path, method));
    }

    private static class MockExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
