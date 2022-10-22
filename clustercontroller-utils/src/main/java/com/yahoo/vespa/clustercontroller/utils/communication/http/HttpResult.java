// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class HttpResult {

    private static class HttpReturnCode {
        private final int code;
        private final String message;

        public HttpReturnCode(int code, String message) {
            this.code = code;
            this.message = message;
        }
        public boolean isSuccess() { return (code >= 200 && code < 300); }
        public int getCode() { return code; }
        public String getMessage() { return message; }
    }
    private HttpReturnCode httpReturnCode;
    private final List<HttpRequest.KeyValuePair> headers;
    private Object content;

    public HttpResult() {
        httpReturnCode = new HttpReturnCode(200, "OK");
        headers = new LinkedList<>();
    }

    public HttpResult(HttpResult other) {
        httpReturnCode = other.httpReturnCode;
        headers = other.headers;
        content = other.content;
    }

    public HttpResult setHttpCode(int code, String description) {
        this.httpReturnCode = new HttpReturnCode(code, description);
        return this;
    }

    public HttpResult setContent(Object content) {
        this.content = content;
        return this;
    }

    public HttpResult addHeader(String key, String value) {
        headers.add(new HttpRequest.KeyValuePair(key, value));
        return this;
    }

    public boolean isSuccess() { return httpReturnCode.isSuccess(); }
    public int getHttpReturnCode() { return httpReturnCode.getCode(); }
    public String getHttpReturnCodeDescription() { return httpReturnCode.getMessage(); }
    public Collection<HttpRequest.KeyValuePair> getHeaders() { return headers; }
    public String getHeader(String key) {
        for (HttpRequest.KeyValuePair p : headers) {
            if (p.getKey().equals(key)) return p.getValue();
        }
        return null;
    }

    public Object getContent() { return content; }

    @Override
    public String toString() { return toString(false); }
    public String toString(boolean verbose) {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP ").append(httpReturnCode.getCode()).append('/').append(httpReturnCode.getMessage());
        if (verbose) {
            for(HttpRequest.KeyValuePair header : headers) {
                sb.append('\n').append(header.getKey()).append(": ").append(header.getValue());
            }
            if (content != null) {
                StringBuilder contentBuilder = new StringBuilder();
                printContent(contentBuilder);
                String s = contentBuilder.toString();
                if (!s.isEmpty()) {
                    sb.append("\n\n").append(s);
                }
            }
        }
        return sb.toString();
    }
    public void printContent(StringBuilder sb) {
        sb.append(content instanceof JsonNode ? ((JsonNode) content).toPrettyString() : content.toString());
    }

}
