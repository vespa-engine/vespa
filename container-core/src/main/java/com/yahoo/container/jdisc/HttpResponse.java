// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.container.logging.AccessLogEntry;
import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.processing.execution.Execution.Trace.LogValue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;

/**
 * An HTTP response as an opaque payload with headers and content type.
 *
 * @author hmusum
 * @author Steinar Knutsen
 */
public abstract class HttpResponse {

    /** Default response content type; text/plain. */
    public static final String DEFAULT_MIME_TYPE = "text/plain";

    /** Default encoding/character set of a HTTP response; UTF-8. */
    public static final String DEFAULT_CHARACTER_ENCODING = "UTF-8";

    private final Response parentResponse;

    /**
     * Creates a new HTTP response
     *
     * @param status the HTTP status code to return with this response (may be changed later)
     * @see Response
     */
    public HttpResponse(int status) {
        parentResponse = com.yahoo.jdisc.http.HttpResponse.newInstance(status);
    }

    /** Marshals this response to the network layer. The caller is responsible for flushing and closing outputStream. */
    public abstract void render(OutputStream outputStream) throws IOException;

    /** The amount of content bytes this response may have in-flight (if positive) before response rendering blocks. */
    public long maxPendingBytes() { return -1; }

    /**
     * Returns the numeric HTTP status code, e.g. 200, 404 and so on.
     *
     * @return the numeric HTTP status code
     */
    public int getStatus() {
        return parentResponse.getStatus();
    }

    /**
     * Sets the numeric HTTP status code this will return.
     */
    public void setStatus(int status) { parentResponse.setStatus(status); }

    /**
     * The response headers.
     *
     * @return a mutable, thread-unsafe view of the response headers
     */
    public HeaderFields headers() {
        return parentResponse.headers();
    }

    /**
     * The underlying JDisc response.
     *
     * @return the actual response which will be used by the JDisc layer
     */
    public com.yahoo.jdisc.Response getJdiscResponse() {
        return parentResponse;
    }

    /**
     * The MIME type of the response contents or null. If null is returned, no
     * content type header is added to the HTTP response.
     *
     * @return by default {@link HttpResponse#DEFAULT_MIME_TYPE}
     */
    public String getContentType() {
        return DEFAULT_MIME_TYPE;
    }

    /**
     * The name of the encoding for the response contents, if applicable. Return
     * null if character set is not applicable to the response in question (e.g.
     * binary formats). If null is returned, not "charset" element is added to
     * the content type header.
     *
     * @return by default {@link HttpResponse#DEFAULT_CHARACTER_ENCODING}
     */
    public String getCharacterEncoding() {
        return DEFAULT_CHARACTER_ENCODING;
    }

    // ========================================
    // Purely optional stuff from this point on
    // ========================================

    /**
     * Override this method to add information from the response to the access log.
     *
     * Remember to also invoke super if you override it.
     *
     * @param accessLogEntry the access log entry to add information to.
     */
    public void populateAccessLogEntry(final AccessLogEntry accessLogEntry) {
        for (LogValue logValue : getLogValues()) {
            accessLogEntry.addKeyValue(logValue.getKey(), logValue.getValue());
        }
    }

    /**
     * Complete creation of this response.
     * This is called by the container once just before writing the response header back to the caller,
     * so this is the last moment at which status and headers can be determined.
     * This default implementation does nothing.
     */
    public void complete() { }

    public Iterable<LogValue> getLogValues() {
        return Collections::emptyIterator;
    }

    /** Sets the type classification of this request for metric collection purposes */
    public void setRequestType(Request.RequestType requestType) { parentResponse.setRequestType(requestType); }

    /**
     * Returns the type classification of this request for metric collection purposes, or null if not set.
     * When not set, the request type will be "read" for GET requests and "write" for other request methods.
     */
    public Request.RequestType getRequestType() { return parentResponse.getRequestType(); }

}
