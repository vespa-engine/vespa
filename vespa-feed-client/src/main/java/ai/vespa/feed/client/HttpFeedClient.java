// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static java.nio.charset.StandardCharsets.UTF_8;
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
    public CircuitBreaker.State circuitBreakerState() {
        return requestStrategy.circuitBreakerState();
    }

    @Override
    public void close(boolean graceful) {
        closed.set(true);
        if (graceful)
            requestStrategy.await();

        requestStrategy.destroy();
    }

    private CompletableFuture<Result> send(String method, DocumentId documentId, String operationJson, OperationParameters params) {
        HttpRequest request = new HttpRequest(method,
                                              getPath(documentId) + getQuery(params),
                                              requestHeaders,
                                              operationJson.getBytes(UTF_8)); // TODO: make it bytes all the way?

        return requestStrategy.enqueue(documentId, request)
                              .thenApply(response -> toResult(request, response, documentId));
    }

    static Result toResult(HttpRequest request, HttpResponse response, DocumentId documentId) {
        Result.Type type;
        switch (response.code()) {
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
            JsonParser parser = factory.createParser(response.body());
            if (parser.nextToken() != JsonToken.START_OBJECT)
                throw new ResultParseException(
                        documentId,
                        "Expected '" + JsonToken.START_OBJECT + "', but found '" + parser.currentToken() + "' in: "
                                + new String(response.body(), UTF_8));

            String name;
            while ((name = parser.nextFieldName()) != null) {
                switch (name) {
                    case "message": message = parser.nextTextValue(); break;
                    case "trace": trace = parser.nextTextValue(); break;
                    default: parser.nextToken();
                }
            }

            if (parser.currentToken() != JsonToken.END_OBJECT)
                throw new ResultParseException(
                        documentId,
                        "Expected '" + JsonToken.END_OBJECT + "', but found '" + parser.currentToken() + "' in: "
                                + new String(response.body(), UTF_8));
        }
        catch (IOException e) {
            throw new ResultParseException(documentId, e);
        }

        if (type == null) // Not a Vespa response, but a failure in the HTTP layer.
            throw new ResultParseException(
                    documentId,
                    "Status " + response.code() + " executing '" + request + "': "
                            + (message == null ? new String(response.body(), UTF_8) : message));

        return new Result(type, documentId, message, trace);
    }

    static String getPath(DocumentId documentId) {
        StringJoiner path = new StringJoiner("/", "/", "");
        path.add("document");
        path.add("v1");
        path.add(encode(documentId.namespace()));
        path.add(encode(documentId.documentType()));
        if (documentId.number().isPresent()) {
            path.add("number");
            path.add(Long.toUnsignedString(documentId.number().getAsLong()));
        }
        else if (documentId.group().isPresent()) {
            path.add("group");
            path.add(encode(documentId.group().get()));
        }
        else {
            path.add("docid");
        }
        path.add(encode(documentId.userSpecific()));

        return path.toString();
    }

    static String encode(String raw) {
        try {
            return URLEncoder.encode(raw, UTF_8.name());
        }
        catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    static String getQuery(OperationParameters params) {
        StringJoiner query = new StringJoiner("&", "?", "").setEmptyValue("");
        if (params.createIfNonExistent()) query.add("create=true");
        params.testAndSetCondition().ifPresent(condition -> query.add("condition=" + encode(condition)));
        params.timeout().ifPresent(timeout -> query.add("timeout=" + timeout.toMillis() + "ms"));
        params.route().ifPresent(route -> query.add("route=" + encode(route)));
        params.tracelevel().ifPresent(tracelevel -> query.add("tracelevel=" + tracelevel));
        return query.toString();
    }

}
