// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.LoggingRequestHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.config.DocumentmanagerConfig;

import com.yahoo.document.json.SingleDocumentParser;
import com.yahoo.document.restapi.OperationHandler;
import com.yahoo.document.restapi.OperationHandlerImpl;
import com.yahoo.document.restapi.Response;
import com.yahoo.document.restapi.RestApiException;
import com.yahoo.document.restapi.RestUri;
import com.yahoo.documentapi.messagebus.MessageBusDocumentAccess;
import com.yahoo.documentapi.messagebus.MessageBusParams;
import com.yahoo.documentapi.messagebus.loadtypes.LoadTypeSet;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * API for handling single operation on a document and visiting.
 *
 * @author Haakon Dybdahl
 */
public class RestApi extends LoggingRequestHandler {

    private static final String CREATE_PARAMETER_NAME = "create";
    private static final String CONDITION_PARAMETER_NAME = "condition";
    private static final String ROUTE_PARAMETER_NAME = "route";
    private static final String DOCUMENTS = "documents";
    private static final String FIELDS = "fields";
    private static final String DOC_ID_NAME = "id";
    private static final String PATH_NAME = "pathId";
    private static final String SELECTION = "selection";
    private static final String CLUSTER = "cluster";
    private static final String CONTINUATION = "continuation";
    private static final String WANTED_DOCUMENT_COUNT = "wantedDocumentCount";
    private static final String FIELD_SET = "fieldSet";
    private static final String CONCURRENCY = "concurrency";
    private static final String APPLICATION_JSON = "application/json";
    private final OperationHandler operationHandler;
    private SingleDocumentParser singleDocumentParser;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicInteger threadsAvailableForApi;

    @Inject
    public RestApi(LoggingRequestHandler.Context parentCtx, DocumentmanagerConfig documentManagerConfig,
                   LoadTypeConfig loadTypeConfig, ThreadpoolConfig threadpoolConfig,
                   MetricReceiver metricReceiver)
    {
        super(parentCtx);
        MessageBusParams params = new MessageBusParams(new LoadTypeSet(loadTypeConfig));
        params.setDocumentmanagerConfig(documentManagerConfig);
        this.operationHandler = new OperationHandlerImpl(new MessageBusDocumentAccess(params), metricReceiver);
        this.singleDocumentParser = new SingleDocumentParser(new DocumentTypeManager(documentManagerConfig));
        // 40% of the threads can be blocked before we deny requests.
        if (threadpoolConfig != null) {
            threadsAvailableForApi = new AtomicInteger(Math.max((int) (0.4 * threadpoolConfig.maxthreads()), 1));
        } else {
            log.warning("No config for threadpool, using 200 for max blocking threads for document rest API.");
            threadsAvailableForApi = new AtomicInteger(200);
        }
    }

    // For testing and development
    public RestApi(
            Executor executor,
            AccessLog accessLog,
            OperationHandler operationHandler,
            int threadsAvailable) {
        super(executor, accessLog, null);
        this.operationHandler = operationHandler;
        this.threadsAvailableForApi = new AtomicInteger(threadsAvailable);
    }
    
    @Override
    public void destroy() {
        operationHandler.shutdown();
    }

    // For testing and development
    protected void setDocTypeManagerForTests(DocumentTypeManager docTypeManager) {
        this.singleDocumentParser = new SingleDocumentParser(docTypeManager);
    }

    private static Optional<String> requestProperty(String parameter, HttpRequest request) {
        final String property = request.getProperty(parameter);
        if (property != null && ! property.isEmpty()) {
            return Optional.of(property);
        }
        return Optional.empty();
    }

    private static boolean parseBooleanStrict(String value) {
        if ("true".equalsIgnoreCase(value)) {
            return true;
        } else if ("false".equalsIgnoreCase(value)) {
            return false;
        }
        throw new IllegalArgumentException(String.format("Value not convertible to bool: '%s'", value));
    }

    private static Optional<Boolean> parseBoolean(String parameter, HttpRequest request) {
        Optional<String> property = requestProperty(parameter, request);
        return property.map(RestApi::parseBooleanStrict);
    }

    private static int parsePositiveInt(String str) throws NumberFormatException {
        int parsed = Integer.parseInt(str);
        if (parsed <= 0) {
            throw new IllegalArgumentException("Parsed number was negative or zero");
        }
        return parsed;
    }

    private static Optional<Integer> parsePositiveIntegerRequestParameter(String parameter, HttpRequest request) throws NumberFormatException {
        Optional<String> property = requestProperty(parameter, request);
        return property.map(RestApi::parsePositiveInt);
    }

    @Override
    public HttpResponse handle(HttpRequest request) {
        try {
            if (threadsAvailableForApi.decrementAndGet() < 1) {
                return Response.createErrorResponse(
                        429 /* Too Many Requests */,
                        "Too many parallel requests, consider using http-vespa-java-client. Please try again later.", RestUri.apiErrorCodes.TOO_MANY_PARALLEL_REQUESTS);
            }
            return handleInternal(request);
        } finally {
            threadsAvailableForApi.incrementAndGet();
        }
    }

    // protected for testing
    protected HttpResponse handleInternal(HttpRequest request) {
        final RestUri restUri;
        try {
            restUri = new RestUri(request.getUri());
        } catch (RestApiException e) {
            return e.getResponse();
        } catch (Exception e2) {
            return Response.createErrorResponse(500, "Exception while parsing URI: " + e2.getMessage(), RestUri.apiErrorCodes.URL_PARSING);
        }

        final Optional<Boolean> create;
        try {
            create = parseBoolean(CREATE_PARAMETER_NAME, request);
        } catch (IllegalArgumentException e) {
            return Response.createErrorResponse(403, "Non valid value for 'create' parameter, must be empty, true, or " +
                    "false: " + request.getProperty(CREATE_PARAMETER_NAME), RestUri.apiErrorCodes.INVALID_CREATE_VALUE);
        }
        String condition = request.getProperty(CONDITION_PARAMETER_NAME);
        Optional<String> route = Optional.ofNullable(request.getProperty(ROUTE_PARAMETER_NAME));

        Optional<ObjectNode> resultJson = Optional.empty();
        try {
            switch (request.getMethod()) {
                case GET:    // Vespa Visit/Get
                    return restUri.getDocId().isEmpty() ? handleVisit(restUri, request) : handleGet(restUri);
                case POST:   // Vespa Put
                    operationHandler.put(restUri, createPutOperation(request, restUri.generateFullId(), condition), route);
                    break;
                case PUT:    // Vespa Update
                    operationHandler.update(restUri, createUpdateOperation(request, restUri.generateFullId(), condition, create), route);
                    break;
                case DELETE: // Vespa Delete
                    operationHandler.delete(restUri, condition, route);
                    break;
                default:
                    return new Response(405, Optional.empty(), Optional.of(restUri));
            }
        } catch (RestApiException e) {
            return e.getResponse();
        } catch (Exception e2) {
            // We always blame the user. This might be a bit nasty, but the parser throws various kind of exception
            // types, but with nice descriptions.
            return Response.createErrorResponse(400, e2.getMessage(), restUri, RestUri.apiErrorCodes.PARSER_ERROR);
        }
        return new Response(200, resultJson, Optional.of(restUri));
    }

    private VespaXMLFeedReader.Operation createPutOperation(HttpRequest request, String id, String condition) {
        final VespaXMLFeedReader.Operation operationPut =
                singleDocumentParser.parsePut(request.getData(), id);
        if (condition != null && ! condition.isEmpty()) {
            operationPut.setCondition(new TestAndSetCondition(condition));
        }
        return operationPut;
    }

    private VespaXMLFeedReader.Operation createUpdateOperation(HttpRequest request, String id, String condition, Optional<Boolean> create) {
        final VespaXMLFeedReader.Operation operationUpdate =
                singleDocumentParser.parseUpdate(request.getData(), id);
        if (condition != null && ! condition.isEmpty()) {
            operationUpdate.getDocumentUpdate().setCondition(new TestAndSetCondition(condition));
        }
        create.ifPresent(c -> operationUpdate.getDocumentUpdate().setCreateIfNonExistent(c));
        return operationUpdate;
    }

    private HttpResponse handleGet(RestUri restUri) throws RestApiException {
        final Optional<String> getDocument = operationHandler.get(restUri);
        final ObjectNode resultNode = mapper.createObjectNode();
        if (getDocument.isPresent()) {
            final JsonNode parseNode;
            try {
                parseNode = mapper.readTree(getDocument.get());
            } catch (IOException e) {
                throw new RuntimeException("Failed while parsing my own results", e);
            }
            resultNode.putPOJO(FIELDS, parseNode.get(FIELDS));
        }
        resultNode.put(DOC_ID_NAME, restUri.generateFullId());
        resultNode.put(PATH_NAME, restUri.getRawPath());

        return new HttpResponse(getDocument.isPresent() ? 200 : 404) {
            @Override
            public String getContentType() { return APPLICATION_JSON; }
            @Override
            public void render(OutputStream outputStream) throws IOException {
                outputStream.write(resultNode.toString().getBytes(StandardCharsets.UTF_8.name()));
            }
        };
    }

    private static HttpResponse createInvalidParameterResponse(String parameter, String explanation) {
        return Response.createErrorResponse(403, String.format("Invalid '%s' value. %s", parameter, explanation), RestUri.apiErrorCodes.UNSPECIFIED);
    }
    
    private HttpResponse handleVisit(RestUri restUri, HttpRequest request) throws RestApiException {
        String documentSelection = Optional.ofNullable(request.getProperty(SELECTION)).orElse("");
        if (restUri.getGroup().isPresent() && ! restUri.getGroup().get().value.isEmpty()) {
            if (! documentSelection.isEmpty()) {
                // TODO why is this restriction in place? Document selection allows composition of location predicate and other expressions
                return Response.createErrorResponse(
                        400,
                        "Visiting does not support setting value for group/value in combination with expression, try using only expression parameter instead.",
                        restUri,
                        RestUri.apiErrorCodes.GROUP_AND_EXPRESSION_ERROR);
            }
            RestUri.Group group = restUri.getGroup().get();
            if (group.name == 'n') {
                documentSelection = "id.user=" + group.value;
            } else {
                documentSelection = "id.group='" + group.value + "'";
            }
        }
        // TODO can refactor this quite a bit with Builder in place...
        Optional<String> cluster = Optional.ofNullable(request.getProperty(CLUSTER));
        Optional<String> continuation = Optional.ofNullable(request.getProperty(CONTINUATION));
        Optional<String> fieldSet = Optional.ofNullable(request.getProperty(FIELD_SET));
        Optional<Integer> wantedDocumentCount;
        try {
            wantedDocumentCount = parsePositiveIntegerRequestParameter(WANTED_DOCUMENT_COUNT, request);
        } catch (IllegalArgumentException e) {
            return createInvalidParameterResponse(WANTED_DOCUMENT_COUNT, "Expected positive integer");
        }
        // TODO refactor
        Optional<Integer> concurrency;
        try {
            concurrency = parsePositiveIntegerRequestParameter(CONCURRENCY, request);
        } catch (IllegalArgumentException e) {
            return createInvalidParameterResponse(CONCURRENCY, "Expected positive integer");
        }

        final OperationHandler.VisitOptions.Builder optionsBuilder = OperationHandler.VisitOptions.builder();
        cluster.ifPresent(c -> optionsBuilder.cluster(c));
        continuation.ifPresent(c -> optionsBuilder.continuation(c));
        wantedDocumentCount.ifPresent(count -> optionsBuilder.wantedDocumentCount(count));
        fieldSet.ifPresent(fs -> optionsBuilder.fieldSet(fs));
        concurrency.ifPresent(c -> optionsBuilder.concurrency(c));

        final OperationHandler.VisitResult visit = operationHandler.visit(restUri, documentSelection, optionsBuilder.build());
        final ObjectNode resultNode = mapper.createObjectNode();
        visit.token.ifPresent(t -> resultNode.put(CONTINUATION, t));
        resultNode.putArray(DOCUMENTS).addPOJO(visit.documentsAsJsonList);
        resultNode.put(PATH_NAME, restUri.getRawPath());

        HttpResponse httpResponse = new HttpResponse(200) {
            @Override
            public String getContentType() { return APPLICATION_JSON; }
            @Override
            public void render(OutputStream outputStream) throws IOException {
                try {
                    outputStream.write(resultNode.toString().getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
        return httpResponse;
    }
}
