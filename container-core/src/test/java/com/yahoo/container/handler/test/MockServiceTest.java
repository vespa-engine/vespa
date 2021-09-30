// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.test;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.filedistribution.fileacquirer.MockFileAcquirer;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.Executor;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class MockServiceTest {

    private final File testFile = new File("src/test/java/com/yahoo/container/handler/test/test.txt");

    @Test
    public void testHandlerTextFormat() throws InterruptedException, IOException {
        HttpResponse response = runHandler(com.yahoo.jdisc.http.HttpRequest.Method.GET, "/foo/bar");
        assertResponse(response, 200, "Hello\nThere!");

        response = runHandler(com.yahoo.jdisc.http.HttpRequest.Method.GET, "http://my.host:8080/foo/bar?key1=foo&key2=bar");
        assertResponse(response, 200, "With params!");

        response = runHandler(com.yahoo.jdisc.http.HttpRequest.Method.PUT, "/bar");
        assertResponse(response, 301, "My data is on a single line");
    }

    @Test
    public void testNoHandlerFound() throws InterruptedException, IOException {
        HttpResponse response = runHandler(com.yahoo.jdisc.http.HttpRequest.Method.DELETE, "/foo/bar");
        assertThat(response.getStatus(), is(404));
        assertResponseContents(response, "DELETE:/foo/bar was not found");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUnknownFileType() throws InterruptedException, IOException {
        runHandlerWithFile(com.yahoo.jdisc.http.HttpRequest.Method.GET, "", new File("nonexistant"));
    }

    @Test(expected = FileNotFoundException.class)
    public void testExceptionResponse() throws InterruptedException, IOException {
        runHandlerWithFile(com.yahoo.jdisc.http.HttpRequest.Method.GET, "", new File("nonexistant.txt"));
    }

    private void assertResponse(HttpResponse response, int expectedCode, String expectedMessage) throws IOException {
        assertThat(response.getStatus(), is(expectedCode));
        assertResponseContents(response, expectedMessage);
    }

    private void assertResponseContents(HttpResponse response, String expected) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        response.render(baos);
        assertThat(baos.toString(), is(expected));
    }

    private void assertResponseOk(HttpResponse response) {
        assertThat(response.getStatus(), is(200));
        assertThat(response.getContentType(), is("text/plain"));
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
