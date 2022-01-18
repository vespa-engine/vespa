// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.handler.test;

import com.yahoo.api.annotations.Beta;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.jdisc.Metric;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * This is a generic http handler that can be used to mock a service when testing your application on jDISC.
 * Configuration and necessary files are given to the handle in its configuration.
 *
 * Example config:
 * <pre>
 * &lt;handler id="MockService"&gt;
 *     &lt;config name="container.handler.test.mockservice"&gt;
 *         &lt;file&gt;myresponses.txt&lt;/file&gt;
 *     &lt;/config&gt;
 *     &lt;binding&gt;http://*\/my/service/path1/*&lt;/binding&gt;
 * &lt;/handler&gt;
 * </pre>
 *
 * The file formats supported out of the box is text, see {@link com.yahoo.container.handler.test.MockService.TextFileHandler}.
 * for descriptions of the format.
 *
 * @author Ulf Lilleengen
 */
@Beta
public class MockService extends ThreadedHttpRequestHandler {

    private final MockServiceHandler handler;

    /**
     * Create a mock service that mocks an external service using data provided via file distribution.
     * A custom handler can be created by subclassing and overriding the createHandler method.
     *
     * @param executor used to create threads
     * @param fileAcquirer used to fetch file from config
     * @param config the mock config for this service
     * @throws InterruptedException if unable to get data file within timeout
     * @throws IOException if unable to create handler due to some IO errors
     */
    public MockService(Executor executor, FileAcquirer fileAcquirer, MockserviceConfig config, Metric metric) throws InterruptedException, IOException {
        super(executor, metric);
        File dataFile = fileAcquirer.waitFor(config.file(), config.fileAcquirerTimeout(), TimeUnit.SECONDS);
        this.handler = createHandler(dataFile);
    }

    /**
     * Create a handler for a file. Override this method to handle a custom file syntax of your own.
     *
     * @param dataFile the file to read
     * @return the handler used to handle requests
     * @throws IOException if errors occurred when loading the file
     */
    protected MockServiceHandler createHandler(File dataFile) throws IOException {
        if (!dataFile.getName().endsWith(".txt")) {
            throw new IllegalArgumentException("Default handler only support .txt files");
        }
        return new TextFileHandler(dataFile);
    }

    @Override
    public final HttpResponse handle(HttpRequest request) {
        try {
            MockServiceHandler.Key key = handler.createKey(request);
            MockServiceHandler.Value value = handler.get(key);
            if (value == null) {
                return new ErrorResponse(404, key + " was not found");
            }
            return new RawResponse(value.returnCode, value.data, value.contentType);
        } catch (Exception e) {
            return new ExceptionResponse(500, e);
        }
    }

    /**
     * A .txt file handler deals with the following format when reading data:
     * method:url:responsecode:data
     *
     * For instance:
     * GET:/my/path1:200:{\"foo\":\"bar\"}
     * PUT:/my/path1:403:{\"error\":\"permission denied\"}
     * TODO: Support binary files
     */
    private static class TextFileHandler implements MockServiceHandler {
        private final Map<MockServiceHandler.Key, Value> store = new HashMap<>();

        public TextFileHandler(File dataFile) throws IOException {
            BufferedReader reader = new BufferedReader(new FileReader(dataFile));
            readInputFile(reader);
        }

        private void readInputFile(BufferedReader reader) throws IOException {
            StringBuilder sb = new StringBuilder();
            int ch;
            char prevChar = 0;
            while ((ch = reader.read()) >= 0) {
                char c = (char) ch;
                if (prevChar == '\n') {
                    if (c == '\n') {
                        parseEntry(sb.toString());
                        sb = new StringBuilder();
                        prevChar = 0;
                        continue;
                    } else {
                        sb.append(prevChar);
                    }
                }
                if (c != '\n') {
                    sb.append(c);
                }
                prevChar = c;
            }
            parseEntry(sb.toString());
        }

        private void parseEntry(String entry) {
            String [] components = entry.split(":", 4);
            MockServiceHandler.Key key = new TextKey(com.yahoo.jdisc.http.HttpRequest.Method.valueOf(components[0]), components[1]);
            Value value = new Value(Integer.parseInt(components[2]), components[3].getBytes(), "text/plain");
            store.put(key, value);
        }

        @Override
        public MockServiceHandler.Key createKey(HttpRequest request) {
            StringBuilder sb = new StringBuilder();
            sb.append(request.getUri().getPath());
            String query = request.getUri().getQuery();
            if (query != null) {
                sb.append("?").append(query);
            }
            return new TextKey(request.getMethod(), sb.toString());
        }

        @Override
        public Value get(MockServiceHandler.Key key) {
            return store.get(key);
        }
    }

    private static class RawResponse extends HttpResponse {
        private final String contentType;
        private final byte[] data;
        RawResponse(int status, byte[] data, String contentType) {
            super(status);
            this.data = data;
            this.contentType = contentType;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            outputStream.write(data);
        }
    }

    private static class ErrorResponse extends RawResponse {
        ErrorResponse(int status, String message) {
            super(status, message.getBytes(), "text/plain");
        }
    }

    private static final class TextKey implements MockServiceHandler.Key {
        private final com.yahoo.jdisc.http.HttpRequest.Method method;
        private final String path;
        public TextKey(com.yahoo.jdisc.http.HttpRequest.Method method, String path) {
            this.method = method;
            this.path = path;
        }

        @Override
        public int hashCode() {
            return path.hashCode() + method.hashCode();
        }

        @Override
        public boolean equals(Object other) {
            if (other.getClass() != TextKey.class) {
                return false;
            }
            TextKey rhs = (TextKey) other;
            return (this.method == rhs.method) &&
                    path.equals(rhs.path);
        }

        @Override
        public String toString() {
            return method.toString() + ":" + path;
        }
    }

    private static class ExceptionResponse extends HttpResponse {
        private final Exception e;
        public ExceptionResponse(int code, Exception e) {
            super(code);
            this.e =e;
        }

        @Override
        public void render(OutputStream outputStream) throws IOException {
            try (PrintStream ps = new PrintStream(outputStream)) {
                e.printStackTrace(ps);
            }
        }
    }

}
