// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.handler;

import com.yahoo.api.annotations.Beta;
import com.yahoo.jdisc.http.HttpHeaders;
import com.yahoo.text.Utf8;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.UnsupportedCharsetException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A response for use with {@link com.yahoo.application.container.JDisc#handleRequest(Request)}.
 *
 * @author Einar M R Rosenvinge
 * @see Request
 */
@Beta
public class Response {

    private final static Pattern charsetPattern = Pattern.compile("charset=([^\\s\\;]+)", Pattern.CASE_INSENSITIVE);
    private final int status;
    private final Headers headers = new Headers();
    private final byte[] body;

    /**
     * Creates a Response with an empty body, and 200 (OK) response code.
     */
    public Response() {
        this(new byte[0]);
    }

    /**
     * Creates a Response with a message body, and 200 (OK) response code.
     *
     * @param body the body of the response
     */
    public Response(byte[] body) {
        this(com.yahoo.jdisc.Response.Status.OK, body);
    }

    /**
     * Creates a Response with a message body, and the given response code.
     *
     * @param status the status code of the response
     * @param body the body of the response
     * @since 5.1.28
     */
    public Response(int status, byte[] body) {
        this.status = status;
        this.body = body;
    }

    /**
     * <p>Returns the status code of this response. This is an integer result code of the attempt to understand and
     * satisfy the corresponding {@link com.yahoo.application.container.handler.Request}.
     *
     * @return The status code.
     * @since 5.1.28
     */
    public int getStatus() {
        return status;
    }

    /**
     * Returns the body of this Response.
     *
     * @return the body of this Response
     */
    public byte[] getBody() {
        return body;
    }

    /**
     * Attempts to decode the buffer returned by {@link #getBody()} as a String in a best-effort manner. This is done
     * using the Content-Type header - and defaults to UTF-8 encoding if the header is unparseable or not found.
     * Note that this may very well throw a {@link CharacterCodingException}.
     *
     * @return a String with the decoded contents of the body buffer
     * @throws CharacterCodingException if the body buffer was not well-formed
     */
    public String getBodyAsString() throws CharacterCodingException {
        CharsetDecoder decoder = charset().newDecoder();
        return decoder.decode(ByteBuffer.wrap(body)).toString();
    }

    /**
     * Returns a mutable multi-map of headers for this Response.
     *
     * @return a mutable multi-map of headers for this Response
     */
    public Headers getHeaders() {
        return headers;
    }

    @Override
    public String toString() {
        String bodyStr = (body == null || body.length == 0) ? "[empty]" : "[omitted]";
        return "Response, headers: " + headers + ", body: " + bodyStr;
    }

    private Charset charset() {
        return charset(headers.getFirst(HttpHeaders.Names.CONTENT_TYPE));
    }

    static Charset charset(String contentType) {
        if (contentType != null) {
            Matcher matcher = charsetPattern.matcher(contentType);
            if (matcher.find()) {
                try {
                    return Charset.forName(matcher.group(1));
                } catch (UnsupportedCharsetException uce) {
                    return Utf8.getCharset();
                }
            }
        }
        return Utf8.getCharset();
    }

}
