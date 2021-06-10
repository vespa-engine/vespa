// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.net.URIBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;

/**
 * HTTP implementation of {@link FeedClient}
 *
 * @author bjorncs
 * @author jonmv
 */
class HttpFeedClient implements FeedClient {

    private static final JsonFactory factory = new JsonFactory();

    private final Map<String, Supplier<String>> requestHeaders;
    private final RequestStrategy requestStrategy;
    private final AtomicBoolean closed = new AtomicBoolean();

    HttpFeedClient(FeedClientBuilder builder) throws IOException {
        this(builder, new HttpRequestStrategy(builder));
    }

    HttpFeedClient(FeedClientBuilder builder, RequestStrategy requestStrategy) {
        this.requestHeaders = new HashMap<>(builder.requestHeaders);
        this.requestStrategy = requestStrategy;
    }

    @Override
    public CompletableFuture<Result> put(DocumentId documentId, String documentJson, OperationParameters params) {
        return send("POST", documentId, requireNonNull(documentJson), params);
    }

    @Override
    public CompletableFuture<Result> update(DocumentId documentId, String updateJson, OperationParameters params) {
        return send("PUT", documentId, requireNonNull(updateJson), params);
    }

    @Override
    public CompletableFuture<Result> remove(DocumentId documentId, OperationParameters params) {
        return send("DELETE", documentId, null, params);
    }

    @Override
    public OperationStats stats() {
        return requestStrategy.stats();
    }

    @Override
    public void close(boolean graceful) {
        closed.set(true);
        if (graceful)
            requestStrategy.await();

        requestStrategy.destroy();
    }

    private void ensureOpen() {
        if (requestStrategy.hasFailed())
            close();

        if (closed.get())
            throw new IllegalStateException("Client is closed, no further operations may be sent");
    }

    private CompletableFuture<Result> send(String method, DocumentId documentId, String operationJson, OperationParameters params) {
        ensureOpen();

        String path = operationPath(documentId, params).toString();
        SimpleHttpRequest request = new SimpleHttpRequest(method, path);
        requestHeaders.forEach((name, value) -> request.setHeader(name, value.get()));
        if (operationJson != null)
            request.setBody(operationJson, ContentType.APPLICATION_JSON);

        return requestStrategy.enqueue(documentId, request)
                              .thenApply(response -> toResult(request, response, documentId));
    }

    static Result toResult(SimpleHttpRequest request, SimpleHttpResponse response, DocumentId documentId) {
        Result.Type type;
        switch (response.getCode()) {
            case 200: type = Result.Type.success; break;
            case 412: type = Result.Type.conditionNotMet; break;
            case 502:
            case 504:
            case 507: type = Result.Type.failure; break;
            default:  type = null;
        }

        String message = null;
        String trace = null;
        try {
            JsonParser parser = factory.createParser(response.getBodyText());
            if (parser.nextToken() != JsonToken.START_OBJECT)
                throw new IllegalArgumentException("Expected '" + JsonToken.START_OBJECT + "', but found '" + parser.currentToken() + "' in: " + response.getBodyText());

            String name;
            while ((name = parser.nextFieldName()) != null) {
                switch (name) {
                    case "message": message = parser.nextTextValue(); break;
                    case "trace": trace = parser.nextTextValue(); break;
                    default: parser.nextToken();
                }
            }

            if (parser.currentToken() != JsonToken.END_OBJECT)
                throw new IllegalArgumentException("Expected '" + JsonToken.END_OBJECT + "', but found '" + parser.currentToken() + "' in: " + response.getBodyText());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        if (type == null) // Not a Vespa response, but a failure in the HTTP layer.
            throw new FeedException("Status " + response.getCode() + " executing '" + request +
                                    "': " + (message == null ? request.getBodyText() : message));

        return new Result(type, documentId, message, trace);
    }

    static List<String> toPath(DocumentId documentId) {
        List<String> path = new ArrayList<>();
        path.add("document");
        path.add("v1");
        path.add(documentId.namespace());
        path.add(documentId.documentType());
        if (documentId.number().isPresent()) {
            path.add("number");
            path.add(Long.toUnsignedString(documentId.number().getAsLong()));
        }
        else if (documentId.group().isPresent()) {
            path.add("group");
            path.add(documentId.group().get());
        }
        else {
            path.add("docid");
        }
        path.add(documentId.userSpecific());

        return path;
    }

    static URI operationPath(DocumentId documentId, OperationParameters params) {
        URIBuilder url = new URIBuilder();
        url.setPathSegments(toPath(documentId));

        if (params.createIfNonExistent()) url.addParameter("create", "true");
        params.testAndSetCondition().ifPresent(condition -> url.addParameter("condition", condition));
        params.timeout().ifPresent(timeout -> url.addParameter("timeout", timeout.toMillis() + "ms"));
        params.route().ifPresent(route -> url.addParameter("route", route));
        params.tracelevel().ifPresent(tracelevel -> url.addParameter("tracelevel", Integer.toString(tracelevel)));

        try {
            return url.build();
        }
        catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

}
