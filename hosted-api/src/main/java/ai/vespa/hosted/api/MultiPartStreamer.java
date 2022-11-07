// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.hosted.api;

import com.yahoo.yolean.Exceptions;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UncheckedIOException;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Used to create builders for multi part http body entities, which stream their data.
 *
 * @author jonmv
 */
public class MultiPartStreamer {

    private final String boundary;
    private final List<Supplier<InputStream>> streams;

    MultiPartStreamer(String boundary) {
        this.boundary = boundary;
        this.streams = new ArrayList<>();
    }

    /** Creates a new MultiPartBodyStreamer in which parts can be aggregated, and then streamed. */
    public MultiPartStreamer() {
        this(UUID.randomUUID().toString());
    }

    /** Adds the given data as a named part in this, using the {@code "text/plain"} content type. */
    public MultiPartStreamer addText(String name, String text) {
        return addData(name, "text/plain", text);
    }

    /** Adds the given data as a named part in this, using the {@code "application/json"} content type. */
    public MultiPartStreamer addJson(String name, String json) {
        return addData(name, "application/json", json);
    }

    /** Adds the given data as a named part in this, using the given content type. */
    public MultiPartStreamer addData(String name, String type, String data) {
        return addData(name, type, null, () -> asStream(data));
    }

    /** Adds the given data as a named part in this, using the {@code "application/octet-stream" content type}. */
    public MultiPartStreamer addBytes(String name, byte[] bytes) {
        return addData(name, "application/octet-stream", null, () -> new ByteArrayInputStream(bytes));
    }

    /** Adds the given data as a named part in this, using the given content type. */
    public MultiPartStreamer addData(String name, String type, String filename, Supplier<InputStream> data) {
        streams.add(() -> separator(name, filename, type));
        streams.add(data);

        return this;
    }

    /** Adds the contents of the file at the given path as a named part in this. */
    public MultiPartStreamer addFile(String name, Path path) {
        String type = Exceptions.uncheck(() -> Files.probeContentType(path));
        return addData(name, type != null ? type : "application/octet-stream", path.getFileName().toString(), () -> asStream(path));
    }

    /**
     * Streams the aggregate of the current parts of this to the given request builder, and returns it.
     * Modifications to this streamer after a request builder has been obtained is not reflected in that builder.
     * This method can be used multiple times, to create new requests.
     * The request builder's method and content should not be set after it has been obtained.
     */
    public HttpRequest.Builder streamTo(HttpRequest.Builder request, Method method) {
        InputStream aggregate = data(); // Get the streams now, not when the aggregate is used.
        return request.setHeader("Content-Type", contentType())
                      .method(method.name(), HttpRequest.BodyPublishers.ofInputStream(() -> aggregate));
    }

    /** Returns an input stream which is an aggregate of all current parts in this, plus an end marker. */
    public InputStream data() {
        InputStream aggregate = new SequenceInputStream(new Enumeration<>() {
            final int j = streams.size();
            int i = -1;
            @Override public boolean hasMoreElements() { return i < j; }
            @Override public InputStream nextElement() { return ++i < j ? streams.get(i).get() : i == j ? end() : null; }
        });
        try {
            if (aggregate.skip(2) != 2)// This should never happen, as the first stream is a ByteArrayInputStream.
                throw new IllegalStateException("Failed skipping extraneous bytes.");
        }
        catch (IOException e) { // This should never happen, as the first stream is a ByteArrayInputStream;
            throw new IllegalStateException("Failed skipping extraneous bytes.", e);
        }
        return new BufferedInputStream(aggregate);
    }

    /** Returns the value of the {@code "Content-Type"} header to use with this. */
    public String contentType() {
        return "multipart/form-data; boundary=" + boundary + "; charset=utf-8";
    }

    /** Returns the separator to put between one part and the next, when this is a string. */
    private InputStream separator(String name, String filename, String contentType) {
        return asStream(disposition(name) + (filename == null ? "" : "; filename=\"" + filename + "\"") + type(contentType));
    }

    /** Returns the end delimiter of the request, with line breaks prepended. */
    private InputStream end() {
        return asStream("\r\n--" + boundary + "--");
    }

    /** Returns the boundary and disposition header for a part, with line breaks prepended. */
    private String disposition(String name) {
        return "\r\n--" + boundary + "\r\n" +
               "Content-Disposition: form-data; name=\"" + name + "\"";
    }

    /** Returns the content type header for a part, with line breaks pre- and appended. */
    private String type(String contentType) {
        return "\r\nContent-Type: " + contentType + "\r\n\r\n";
    }

    /** Returns a ByteArrayInputStream over the given string, UTF-8 encoded. */
    private static InputStream asStream(String string) {
        return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns an InputStream over the file at the given path — rethrows any IOException as UncheckedIOException. */
    private InputStream asStream(Path path) {
        try {
            return Files.newInputStream(path);
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
