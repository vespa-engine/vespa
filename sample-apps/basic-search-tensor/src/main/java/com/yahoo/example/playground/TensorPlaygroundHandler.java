// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.example.playground;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler;

import java.io.*;
import java.net.URI;
import java.util.concurrent.Executor;

public class TensorPlaygroundHandler extends ThreadedHttpRequestHandler {

    public TensorPlaygroundHandler(Executor executor) {
        super(executor);
    }

    @Override
    public HttpResponse handle(HttpRequest httpRequest) {

        if (!"playground".equals(getPath(httpRequest, 0))) {
            return error(httpRequest, "Invalid path");
        }

        if ("eval".equals(getPath(httpRequest, 1))) {
            return eval(httpRequest);
        }

        return file(httpRequest);
    }


    private HttpResponse eval(HttpRequest httpRequest) {
        String json = getParam(httpRequest, "json");
        if (json == null) {
            return error(httpRequest, "Eval operation requires an expression to evaluate");
        }
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream stream) throws IOException {
                String result = ExpressionEvaluator.evaluate(json);
                try (PrintWriter writer = new PrintWriter(stream)) {
                    writer.print(result);
                }
            }
            @Override
            public String getContentType() {
                return "application/json";
            }
        };
    }

    private HttpResponse file(HttpRequest httpRequest) {
        final String filename = getPath(httpRequest, "playground/index.html");
        if (!resourceExists(filename)) {
            return error(httpRequest, "Error retrieving " + filename);
        }
        return new HttpResponse(200) {
            @Override
            public void render(OutputStream outputStream) throws IOException {
                try(InputStream inputStream = resourceAsStream(filename)) {
                    copy(inputStream, outputStream);
                }
            }
            @Override
            public String getContentType() {
                return contentType(filename);
            }
        };
    }

    private HttpResponse error(HttpRequest httpRequest, String msg) {
        return error(httpRequest, msg, 400);
    }

    private HttpResponse error(HttpRequest httpRequest, String msg, int status) {
        return new HttpResponse(status) {
            @Override
            public void render(OutputStream stream) throws IOException {
                try (PrintWriter writer = new PrintWriter(stream)) {
                    writer.print(msg);
                }
            }
        };
    }

    private static void copy(InputStream source, OutputStream sink) throws IOException {
        byte[] buf = new byte[4096];
        int n;
        while ((n = source.read(buf)) > 0) {
            sink.write(buf, 0, n);
        }
    }

    private static String contentType(String fname) {
        switch (fname.substring(fname.lastIndexOf(".") + 1)) {
            case "html": return "text/html";
            case "css":  return "text/css";
            case "js":   return "application/javascript";
            case "json": return "application/json";
        }
        return "text/plain";
    }

    private boolean resourceExists(String filename) {
        return getClass().getClassLoader().getResource(filename) != null;
    }

    private InputStream resourceAsStream(String filename) {
        return getClass().getClassLoader().getResourceAsStream(filename);
    }

    private String getPath(HttpRequest httpRequest) {
        return getPath(httpRequest, null);
    }

    private String getPath(HttpRequest httpRequest, String defaultPath) {
        URI uri = httpRequest.getUri();
        String path = uri.getPath();
        if (path == null || path.length() == 0) {
            return defaultPath;
        }
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private String getPath(HttpRequest httpRequest, int level) {
        String[] path = getPath(httpRequest).split("/");
        return (level >= 0 && level < path.length) ? path[level].toLowerCase() : null;
    }

    private String getParam(HttpRequest httpRequest, String param) {
        return httpRequest.getProperty(param);
    }

}
