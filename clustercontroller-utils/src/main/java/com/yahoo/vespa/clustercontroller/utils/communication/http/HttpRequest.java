// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.communication.http;

import com.yahoo.vespa.clustercontroller.utils.util.CertainlyCloneable;

import java.util.LinkedList;
import java.util.List;

public class HttpRequest extends CertainlyCloneable<HttpRequest> {

    public static class KeyValuePair {
        public String key;
        public String value;

        public KeyValuePair(String k, String v) { this.key = k; this.value = v; }

        public String getValue() {
            return value;
        }

        public String getKey() {
            return key;
        }
    }

    public enum HttpOp { GET, POST, PUT, DELETE }

    private String scheme;
    private String host;
    private int port;
    private String path;
    private List<KeyValuePair> urlOptions = new LinkedList<>();
    private List<KeyValuePair> headers = new LinkedList<>();
    private long timeoutMillis;
    private Object postContent;
    private HttpOp httpOperation;

    public HttpRequest() {}

    public String getScheme() { return scheme; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getPath() { return path; }
    public List<KeyValuePair> getUrlOptions() { return urlOptions; }
    public String getOption(String key, String defaultValue) {
        for (KeyValuePair value : urlOptions) {
            if (value.key.equals(key)) return value.value;
        }
        return defaultValue;

    }
    public List<KeyValuePair> getHeaders() { return headers; }
    public String getHeader(String key, String defaultValue) {
        for (KeyValuePair header : headers) {
            if (header.key.equals(key)) return header.value;
        }
        return defaultValue;
    }
    public long getTimeoutMillis() { return timeoutMillis; }
    public Object getPostContent() { return postContent; }
    public HttpOp getHttpOperation() { return httpOperation; }

    public HttpRequest setScheme(String scheme) { this.scheme = scheme; return this; }
    public HttpRequest setHost(String hostname) { this.host = hostname; return this; }
    public HttpRequest setPort(int port) { this.port = port; return this; }
    public HttpRequest setPath(String path) {
        this.path = path; return this;
    }
    public HttpRequest addUrlOption(String key, String value) { this.urlOptions.add(new KeyValuePair(key, value)); return this; }
    public HttpRequest setUrlOptions(List<KeyValuePair> options) { this.urlOptions.clear(); this.urlOptions.addAll(options); return this; }
    public HttpRequest addHttpHeader(String key, String value) { this.headers.add(new KeyValuePair(key, value)); return this; }
    public HttpRequest setTimeout(long timeoutMillis) { this.timeoutMillis = timeoutMillis; return this; }
    public HttpRequest setPostContent(Object content) { this.postContent = content; return this; }
    public HttpRequest setHttpOperation(HttpOp op) { this.httpOperation = op; return this; }

    /** Create a copy of this request, and override what is specified in the input in the new request. */
    public HttpRequest merge(HttpRequest r) {
        HttpRequest copy = clone();
        if (r.scheme != null) copy.scheme = r.scheme;
        if (r.host != null) copy.host = r.host;
        if (r.port != 0) copy.port = r.port;
        if (r.path != null) copy.path = r.path;
        for (KeyValuePair h : r.headers) {
            boolean containsElement = false;
            for (KeyValuePair h2 : copy.headers) { containsElement |= (h.key.equals(h2.key)); }
            if (!containsElement) copy.headers.add(h);
        }
        for (KeyValuePair h : r.urlOptions) {
            boolean containsElement = false;
            for (KeyValuePair h2 : copy.urlOptions) { containsElement |= (h.key.equals(h2.key)); }
            if (!containsElement) copy.urlOptions.add(h);
        }
        if (r.timeoutMillis != 0) copy.timeoutMillis = r.timeoutMillis;
        if (r.postContent != null) copy.postContent = r.postContent;
        if (r.httpOperation != null) copy.httpOperation = r.httpOperation;
        return copy;
    }

    @Override
    public HttpRequest clone() {
        HttpRequest r = (HttpRequest) super.clone();
        r.headers = new LinkedList<>(r.headers);
        r.urlOptions = new LinkedList<>(r.urlOptions);
        return r;
    }

    @Override
    public String toString() { return toString(false); }
    public String toString(boolean verbose) {
        String httpOp = (httpOperation != null ? httpOperation.toString()
                                               : (postContent == null ? "GET?" : "POST?"));
        StringBuilder sb = new StringBuilder().append(httpOp).append(" ").append(scheme).append(':');
        if (host != null) {
            sb.append("//").append(host);
            if (port != 0) sb.append(':').append(port);
        }
        if (path == null || path.isEmpty()) {
            sb.append('/');
        } else {
            if (path.charAt(0) != '/') sb.append('/');
            sb.append(path);
        }
        if (urlOptions != null && urlOptions.size() > 0) {
            boolean first = (path == null || path.indexOf('?') < 0);
            for (KeyValuePair e : urlOptions) {
                sb.append(first ? '?' : '&');
                first = false;
                sb.append(e.key).append('=').append(e.value);
            }
        }
        if (verbose) {
            for (KeyValuePair p : headers) {
                sb.append('\n').append(p.key).append(": ").append(p.value);
            }
            if (postContent != null && !postContent.toString().isEmpty()) {
                sb.append("\n\n").append(postContent.toString());
            }
        }
        return sb.toString();
    }

    public void verifyComplete() {
        if (path == null) throw new IllegalStateException("HTTP requests must have a path set. Use '/' for top level");
        if (httpOperation == null) throw new IllegalStateException("HTTP requests must have an HTTP method defined");
    }

    public boolean equals(Object o) {
        // Equals is only used for debugging as far as we know. Refer to verbose toString to simplify
        if (o instanceof HttpRequest) {
            return toString(true).equals(((HttpRequest) o).toString(true));
        }
        return false;
    }

    public int hashCode() {
        // not used, only here to match equals() and avoid lint warning
        return toString(true).hashCode();
    }
}
