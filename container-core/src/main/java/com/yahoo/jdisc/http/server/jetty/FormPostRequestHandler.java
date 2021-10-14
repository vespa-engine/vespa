// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.google.common.base.Preconditions;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.handler.AbstractRequestHandler;
import com.yahoo.jdisc.handler.CompletionHandler;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.DelegatedRequestHandler;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpRequest;

import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.yahoo.jdisc.Response.Status.UNSUPPORTED_MEDIA_TYPE;
import static com.yahoo.jdisc.http.server.jetty.CompletionHandlerUtils.NOOP_COMPLETION_HANDLER;

/**
 * Request handler that wraps POST requests of application/x-www-form-urlencoded data.
 *
 * The wrapper defers invocation of the "real" request handler until it has read the request content (body),
 * parsed the form parameters and merged them into the request's parameters.
 *
 * @author bakksjo
 * $Id$
 */
class FormPostRequestHandler extends AbstractRequestHandler implements ContentChannel, DelegatedRequestHandler {

    private final ByteArrayOutputStream accumulatedRequestContent = new ByteArrayOutputStream();
    private final RequestHandler delegateHandler;
    private final String contentCharsetName;
    private final boolean removeBody;

    private Charset contentCharset;
    private HttpRequest request;
    private ResourceReference requestReference;
    private ResponseHandler responseHandler;

    /**
     * @param delegateHandler the "real" request handler that this handler wraps
     * @param contentCharsetName name of the charset to use when interpreting the content data
     */
    public FormPostRequestHandler(
            final RequestHandler delegateHandler,
            final String contentCharsetName,
            final boolean removeBody) {
        this.delegateHandler = Objects.requireNonNull(delegateHandler);
        this.contentCharsetName = Objects.requireNonNull(contentCharsetName);
        this.removeBody = removeBody;
    }

    @Override
    public ContentChannel handleRequest(final Request request, final ResponseHandler responseHandler) {
        Preconditions.checkArgument(request instanceof HttpRequest, "Expected HttpRequest, got " + request);
        Objects.requireNonNull(responseHandler, "responseHandler");

        this.contentCharset = getCharsetByName(contentCharsetName);
        this.responseHandler = responseHandler;
        this.request = (HttpRequest) request;
        this.requestReference = request.refer(this);

        return this;
    }

    @Override
    public void write(final ByteBuffer buf, final CompletionHandler completionHandler) {
        assert buf.hasArray();
        accumulatedRequestContent.write(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
        completionHandler.completed();
    }

    @SuppressWarnings("try")
    @Override
    public void close(final CompletionHandler completionHandler) {
        try (final ResourceReference ref = requestReference) {
            final byte[] requestContentBytes = accumulatedRequestContent.toByteArray();
            final String content = new String(requestContentBytes, contentCharset);
            completionHandler.completed();
            final Map<String, List<String>> parameterMap = parseFormParameters(content);
            mergeParameters(parameterMap, request.parameters());
            final ContentChannel contentChannel = delegateHandler.handleRequest(request, responseHandler);
            if (contentChannel != null) {
                if (!removeBody) {
                    final ByteBuffer byteBuffer = ByteBuffer.wrap(requestContentBytes);
                    contentChannel.write(byteBuffer, NOOP_COMPLETION_HANDLER);
                }
                contentChannel.close(NOOP_COMPLETION_HANDLER);
            }
        }
    }

    /**
     * Looks up a Charset given a charset name.
     *
     * @param charsetName the name of the charset to look up
     * @return a valid Charset for the charset name (never returns null)
     * @throws RequestException if the charset name is invalid or unsupported
     */
    private static Charset getCharsetByName(final String charsetName) throws RequestException {
        try {
            final Charset charset = Charset.forName(charsetName);
            if (charset == null) {
                throw new RequestException(UNSUPPORTED_MEDIA_TYPE, "Unsupported charset " + charsetName);
            }
            return charset;
        } catch (final IllegalCharsetNameException |UnsupportedCharsetException e) {
            throw new RequestException(UNSUPPORTED_MEDIA_TYPE, "Unsupported charset " + charsetName, e);
        }
    }

    /**
     * Parses application/x-www-form-urlencoded data into a map of parameters.
     *
     * @param formContent raw form content data (body)
     * @return map of decoded parameters
     */
    private static Map<String, List<String>> parseFormParameters(final String formContent) {
        if (formContent.isEmpty()) {
            return Collections.emptyMap();
        }

        final Map<String, List<String>> parameterMap = new HashMap<>();
        final String[] params = formContent.split("&");
        for (final String param : params) {
            final String[] parts = param.split("=");
            final String paramName = urlDecode(parts[0]);
            final String paramValue = parts.length > 1 ? urlDecode(parts[1]) : "";
            List<String> currentValues = parameterMap.get(paramName);
            if (currentValues == null) {
                currentValues = new LinkedList<>();
                parameterMap.put(paramName, currentValues);
            }
            currentValues.add(paramValue);
        }
        return parameterMap;
    }

    /**
     * Percent-decoding method that doesn't throw.
     *
     * @param encoded percent-encoded data
     * @return decoded data
     */
    private static String urlDecode(final String encoded) {
        try {
            // Regardless of the charset used to transfer the request body,
            // all percent-escaping of non-ascii characters should use UTF-8 code points.
            return URLDecoder.decode(encoded, StandardCharsets.UTF_8.name());
        } catch (final UnsupportedEncodingException e) {
            // Unfortunately, there is no URLDecoder.decode() method that takes a Charset, so we have to deal
            // with this exception.
            throw new IllegalStateException("Whoa, JVM doesn't support UTF-8 today.", e);
        }
    }

    /**
     * Merges source parameters into a destination map.
     *
     * @param source containing the parameters to copy into the destination
     * @param destination receiver of parameters, possibly already containing data
     */
    private static void mergeParameters(
            final Map<String,List<String>> source,
            final Map<String,List<String>> destination) {
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            final List<String> destinationValues = destination.get(entry.getKey());
            if (destinationValues != null) {
                destinationValues.addAll(entry.getValue());
            } else {
                destination.put(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public RequestHandler getDelegate() {
        return delegateHandler;
    }
}
