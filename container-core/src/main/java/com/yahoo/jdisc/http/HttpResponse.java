// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

import com.yahoo.jdisc.HeaderFields;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;

import java.util.List;

/**
 * A HTTP response.
 *
 * @author Einar M R Rosenvinge
 */
public class HttpResponse extends Response {

    private final HeaderFields trailers = new HeaderFields();
    private boolean chunkedEncodingEnabled = true;
    private String message;

    public interface Status extends Response.Status {
        int REQUEST_ENTITY_TOO_LARGE = REQUEST_TOO_LONG;
        int REQUEST_RANGE_NOT_SATISFIABLE = REQUESTED_RANGE_NOT_SATISFIABLE;
    }

    protected HttpResponse(Request request, int status, String message, Throwable error) {
        super(status, error);
        this.message = message;
    }

    public boolean isChunkedEncodingEnabled() {
        if (headers().contains(HttpHeaders.Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED)) {
            return true;
        }
        if (headers().containsKey(HttpHeaders.Names.CONTENT_LENGTH)) {
            return false;
        }
        return chunkedEncodingEnabled;
    }

    public void setChunkedEncodingEnabled(boolean chunkedEncodingEnabled) {
        this.chunkedEncodingEnabled = chunkedEncodingEnabled;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void copyHeaders(HeaderFields target) {
        target.addAll(headers());
    }

    public List<Cookie> decodeSetCookieHeader() {
        return CookieHelper.decodeSetCookieHeader(headers());
    }

    public void encodeSetCookieHeader(List<Cookie> cookies) {
        CookieHelper.encodeSetCookieHeader(headers(), cookies);
    }

    /**
     * <p>Returns the set of trailer header fields of this HttpResponse. These are typically meta-data that should have
     * been part of {@link #headers()}, but were not available prior to calling {@link
     * ResponseHandler#handleResponse(Response)}. You must NOT WRITE to these headers AFTER calling {@link
     * ContentChannel#close(CompletionHandler)}, and you must NOT READ from these headers BEFORE {@link
     * ContentChannel#close(CompletionHandler)} has been called.</p>
     *
     * <p><b>NOTE:</b> These headers are NOT thread-safe. You need to explicitly synchronized on the returned object to
     * prevent concurrency issues such as ConcurrentModificationExceptions.</p>
     *
     * @return The trailer headers of this HttpRequest.
     */
    public HeaderFields trailers() {
        return trailers;
    }

    public static boolean isServerError(Response response) {
        return (response.getStatus() >= 500) && (response.getStatus() < 600);
    }

    public static HttpResponse newInstance(int status) {
        return new HttpResponse(null, status, null, null);
    }

    public static HttpResponse newInstance(int status, String message) {
        return new HttpResponse(null, status, message, null);
    }

    public static HttpResponse newError(Request request, int status, Throwable error) {
        return new HttpResponse(request, status, formatMessage(error), error);
    }

    public static HttpResponse newInternalServerError(Request request, Throwable error) {
        return new HttpResponse(request, Status.INTERNAL_SERVER_ERROR, formatMessage(error), error);
    }

    private static String formatMessage(Throwable t) {
        String msg = t.getMessage();
        return msg != null ? msg : t.toString();
    }

}
