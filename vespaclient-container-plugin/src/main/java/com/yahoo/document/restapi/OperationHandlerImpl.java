// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.restapi.resource.RestApi;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentAccessException;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.SyncSession;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.MessageBusSyncSession;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentapi.metrics.DocumentApiMetrics;
import com.yahoo.documentapi.metrics.DocumentOperationStatus;
import com.yahoo.documentapi.metrics.DocumentOperationType;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.metrics.simple.MetricReceiver;
import com.yahoo.storage.searcher.ContinuationHit;
import com.yahoo.vdslib.VisitorOrdering;
import com.yahoo.vespaclient.ClusterDef;
import com.yahoo.vespaclient.ClusterList;
import com.yahoo.vespaxmlparser.VespaXMLFeedReader;
import com.yahoo.yolean.concurrent.ConcurrentResourcePool;
import com.yahoo.yolean.concurrent.ResourceFactory;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Sends operations to messagebus via document api.
 *
 * @author dybis 
 */
public class OperationHandlerImpl implements OperationHandler {

    public interface ClusterEnumerator {
        List<ClusterDef> enumerateClusters();
    }

    public interface BucketSpaceResolver {
        Optional<String> clusterBucketSpaceFromDocumentType(String clusterConfigId, String docType);
    }

    public static class BucketSpaceRoute {
        private final String clusterRoute;
        private final String bucketSpace;

        public BucketSpaceRoute(String clusterRoute, String bucketSpace) {
            this.clusterRoute = clusterRoute;
            this.bucketSpace = bucketSpace;
        }

        public String getClusterRoute() {
            return clusterRoute;
        }

        public String getBucketSpace() {
            return bucketSpace;
        }
    }

    public static final int VISIT_TIMEOUT_MS = 120000;
    public static final int WANTED_DOCUMENT_COUNT_UPPER_BOUND = 1000; // Approximates the max default size of a bucket
    private final DocumentAccess documentAccess;
    private final DocumentApiMetrics metricsHelper;
    private final ClusterEnumerator clusterEnumerator;
    private final BucketSpaceResolver bucketSpaceResolver;

    private static final class SyncSessionFactory extends ResourceFactory<SyncSession> {
        private final DocumentAccess documentAccess;
        SyncSessionFactory(DocumentAccess documentAccess) {
            this.documentAccess = documentAccess;
        }
        @Override
        public SyncSession create() {
            return documentAccess.createSyncSession(new SyncParameters.Builder().build());
        }
    }

    private final ConcurrentResourcePool<SyncSession> syncSessions;

    private static ClusterEnumerator defaultClusterEnumerator() {
        return () -> new ClusterList("client").getStorageClusters();
    }

    private static BucketSpaceResolver defaultBucketResolver() {
        return (clusterConfigId, docType) -> Optional.ofNullable(BucketSpaceEnumerator
                .fromConfig(clusterConfigId).getDoctypeToSpaceMapping()
                .get(docType));
    }

    public OperationHandlerImpl(DocumentAccess documentAccess, MetricReceiver metricReceiver) {
        this(documentAccess, defaultClusterEnumerator(), defaultBucketResolver(), metricReceiver);
    }

    public OperationHandlerImpl(DocumentAccess documentAccess, ClusterEnumerator clusterEnumerator,
                                BucketSpaceResolver bucketSpaceResolver, MetricReceiver metricReceiver) {
        this.documentAccess = documentAccess;
        this.clusterEnumerator = clusterEnumerator;
        this.bucketSpaceResolver = bucketSpaceResolver;
        syncSessions = new ConcurrentResourcePool<>(new SyncSessionFactory(documentAccess));
        metricsHelper = new DocumentApiMetrics(metricReceiver, "documentV1");
    }

    @Override
    public void shutdown() {
        for (SyncSession session : syncSessions) {
            session.destroy();
        }
        documentAccess.shutdown();
    }

    private static final int HTTP_STATUS_BAD_REQUEST = 400;
    private static final int HTTP_STATUS_INSUFFICIENT_STORAGE = 507;
    private static final int HTTP_PRE_CONDIDTION_FAILED = 412;

    public static int getHTTPStatusCode(Set<Integer> errorCodes) {
        if (errorCodes.size() == 1 && errorCodes.contains(DocumentProtocol.ERROR_NO_SPACE)) {
            return HTTP_STATUS_INSUFFICIENT_STORAGE;
        }
        if (errorCodes.contains(DocumentProtocol.ERROR_TEST_AND_SET_CONDITION_FAILED)) {
            return HTTP_PRE_CONDIDTION_FAILED;
        }
        return HTTP_STATUS_BAD_REQUEST;
    }

    private static Response createErrorResponse(DocumentAccessException documentException, RestUri restUri) {
        if (documentException.hasConditionNotMetError()) {
            return Response.createErrorResponse(getHTTPStatusCode(documentException.getErrorCodes()), "Condition did not match document.",
                    restUri, RestUri.apiErrorCodes.DOCUMENT_CONDITION_NOT_MET);
        }
        return Response.createErrorResponse(getHTTPStatusCode(documentException.getErrorCodes()), documentException.getMessage(), restUri,
                RestUri.apiErrorCodes.DOCUMENT_EXCPETION);
    }

    @Override
    public VisitResult visit(RestUri restUri, String documentSelection, VisitOptions options) throws RestApiException {
        VisitorParameters visitorParameters = createVisitorParameters(restUri, documentSelection, options);

        VisitorControlHandler visitorControlHandler = new VisitorControlHandler();
        visitorParameters.setControlHandler(visitorControlHandler);
        LocalDataVisitorHandler localDataVisitorHandler = new LocalDataVisitorHandler();
        visitorParameters.setLocalDataHandler(localDataVisitorHandler);

        final VisitorSession visitorSession;
        try {
            visitorSession = documentAccess.createVisitorSession(visitorParameters);
            // Not sure if this line is required
            visitorControlHandler.setSession(visitorSession);
        } catch (Exception e) {
            throw new RestApiException(Response.createErrorResponse(
                    500,
                    "Failed during parsing of arguments for visiting: " + ExceptionUtils.getStackTrace(e),
                    restUri,
                    RestUri.apiErrorCodes.VISITOR_ERROR));
        }
        try {
            return doVisit(visitorControlHandler, localDataVisitorHandler, restUri);
        } finally {
            visitorSession.destroy();
        }
    }

    private static void throwIfFatalVisitingError(VisitorControlHandler handler, RestUri restUri) throws RestApiException {
        final VisitorControlHandler.Result result = handler.getResult();
        if (result.getCode() == VisitorControlHandler.CompletionCode.TIMEOUT) {
            if (! handler.hasVisitedAnyBuckets()) {
                throw new RestApiException(Response.createErrorResponse(500, "Timed out", restUri, RestUri.apiErrorCodes.TIME_OUT));
            } // else: some progress has been made, let client continue with new token.
        } else if (result.getCode() != VisitorControlHandler.CompletionCode.SUCCESS) {
            throw new RestApiException(Response.createErrorResponse(400, result.toString(), RestUri.apiErrorCodes.VISITOR_ERROR));
        }
    }

    private VisitResult doVisit(
            VisitorControlHandler visitorControlHandler,
            LocalDataVisitorHandler localDataVisitorHandler,
            RestUri restUri) throws RestApiException {
        try {
            visitorControlHandler.waitUntilDone(); // VisitorParameters' session timeout implicitly triggers timeout failures.
            throwIfFatalVisitingError(visitorControlHandler, restUri);
        } catch (InterruptedException e) {
            throw new RestApiException(Response.createErrorResponse(500, ExceptionUtils.getStackTrace(e), restUri, RestUri.apiErrorCodes.INTERRUPTED));
        }
        if (localDataVisitorHandler.getErrors().isEmpty()) {
            final Optional<String> continuationToken;
            if (! visitorControlHandler.getProgress().isFinished()) {
                final ContinuationHit continuationHit = new ContinuationHit(visitorControlHandler.getProgress());
                continuationToken = Optional.of(continuationHit.getValue());
            } else {
                continuationToken = Optional.empty();
            }
            return new VisitResult(continuationToken, localDataVisitorHandler.getCommaSeparatedJsonDocuments());
        }
        throw new RestApiException(Response.createErrorResponse(500, localDataVisitorHandler.getErrors(), restUri, RestUri.apiErrorCodes.UNSPECIFIED));
    }

    private void setRoute(SyncSession session, Optional<String> route) throws RestApiException {
        if (! (session instanceof MessageBusSyncSession)) {
            // Not sure if this ever could happen but better be safe.
            throw new RestApiException(Response.createErrorResponse(
                    400, "Can not set route since the API is not using message bus.", RestUri.apiErrorCodes.NO_ROUTE_WHEN_NOT_PART_OF_MESSAGEBUS));
        }
        ((MessageBusSyncSession) session).setRoute(route.orElse("default"));
    }

    @Override
    public void put(RestUri restUri, VespaXMLFeedReader.Operation data, Optional<String> route) throws RestApiException {
        SyncSession syncSession = syncSessions.alloc();
        Response response;
        try {
            Instant startTime = Instant.now();
            DocumentPut put = new DocumentPut(data.getDocument());
            put.setCondition(data.getCondition());
            setRoute(syncSession, route);
            syncSession.put(put);
            metricsHelper.reportSuccessful(DocumentOperationType.PUT, startTime);
            return;
        } catch (DocumentAccessException documentException) {
            response = createErrorResponse(documentException, restUri);
        } catch (Exception e) {
            response = Response.createErrorResponse(500, ExceptionUtils.getStackTrace(e), restUri, RestUri.apiErrorCodes.INTERNAL_EXCEPTION);
        } finally {
            syncSessions.free(syncSession);
        }

        metricsHelper.reportFailure(DocumentOperationType.PUT, DocumentOperationStatus.fromHttpStatusCode(response.getStatus()));
        throw new RestApiException(response);
    }

    @Override
    public void update(RestUri restUri, VespaXMLFeedReader.Operation data, Optional<String> route) throws RestApiException {
        SyncSession syncSession = syncSessions.alloc();
        Response response;
        try {
            Instant startTime = Instant.now();
            setRoute(syncSession, route);
            syncSession.update(data.getDocumentUpdate());
            metricsHelper.reportSuccessful(DocumentOperationType.UPDATE, startTime);
            return;
        } catch (DocumentAccessException documentException) {
            response = createErrorResponse(documentException, restUri);
        } catch (Exception e) {
            response = Response.createErrorResponse(500, ExceptionUtils.getStackTrace(e), restUri, RestUri.apiErrorCodes.INTERNAL_EXCEPTION);
        } finally {
            syncSessions.free(syncSession);
        }

        metricsHelper.reportFailure(DocumentOperationType.UPDATE, DocumentOperationStatus.fromHttpStatusCode(response.getStatus()));
        throw new RestApiException(response);
    }

    @Override
    public void delete(RestUri restUri, String condition, Optional<String> route) throws RestApiException {
        SyncSession syncSession = syncSessions.alloc();
        Response response;
        try {
            Instant startTime = Instant.now();
            DocumentId id = new DocumentId(restUri.generateFullId());
            DocumentRemove documentRemove = new DocumentRemove(id);
            setRoute(syncSession, route);
            if (condition != null && ! condition.isEmpty()) {
                documentRemove.setCondition(new TestAndSetCondition(condition));
            }
            syncSession.remove(documentRemove);
            metricsHelper.reportSuccessful(DocumentOperationType.REMOVE, startTime);
            return;
        } catch (DocumentAccessException documentException) {
            if (documentException.hasConditionNotMetError()) {
                response = Response.createErrorResponse(412, "Condition not met: " + documentException.getMessage(),
                        restUri, RestUri.apiErrorCodes.DOCUMENT_CONDITION_NOT_MET);
            } else {
                response = Response.createErrorResponse(400, documentException.getMessage(), restUri, RestUri.apiErrorCodes.DOCUMENT_EXCPETION);
            }
        } catch (Exception e) {
            response = Response.createErrorResponse(500, ExceptionUtils.getStackTrace(e), restUri, RestUri.apiErrorCodes.UNSPECIFIED);
        } finally {
            syncSessions.free(syncSession);
        }

        metricsHelper.reportFailure(DocumentOperationType.REMOVE, DocumentOperationStatus.fromHttpStatusCode(response.getStatus()));
        throw new RestApiException(response);
    }

    @Override
    public Optional<String> get(RestUri restUri) throws RestApiException {
        SyncSession syncSession = syncSessions.alloc();
        setRoute(syncSession, Optional.empty());
        try {
            DocumentId id = new DocumentId(restUri.generateFullId());
            final Document document = syncSession.get(id, restUri.getDocumentType() + ":[document]", DocumentProtocol.Priority.NORMAL_1);
            if (document == null) {
                return Optional.empty();
            }
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            JsonWriter jsonWriter = new JsonWriter(outputStream);
            jsonWriter.write(document);
            return Optional.of(outputStream.toString(StandardCharsets.UTF_8.name()));

        } catch (Exception e) {
            throw new RestApiException(Response.createErrorResponse(500, ExceptionUtils.getStackTrace(e), restUri, RestUri.apiErrorCodes.UNSPECIFIED));
        } finally {
            syncSessions.free(syncSession);
        }
    }

    protected BucketSpaceRoute resolveBucketSpaceRoute(Optional<String> wantedCluster, String docType) throws RestApiException {
        final List<ClusterDef> clusters = clusterEnumerator.enumerateClusters();
        ClusterDef clusterDef = resolveClusterDef(wantedCluster, clusters);
        Optional<String> targetBucketSpace = bucketSpaceResolver.clusterBucketSpaceFromDocumentType(clusterDef.getConfigId(), docType);
        if (!targetBucketSpace.isPresent()) {
            throw new RestApiException(Response.createErrorResponse(400, String.format(
                    "Document type '%s' in cluster '%s' is not mapped to a known bucket space", docType, clusterDef.getName()),
                    RestUri.apiErrorCodes.UNKNOWN_BUCKET_SPACE)); // TODO own code
        }
        return new BucketSpaceRoute(clusterDefToRoute(clusterDef), targetBucketSpace.get());
    }

    protected static ClusterDef resolveClusterDef(Optional<String> wantedCluster, List<ClusterDef> clusters) throws RestApiException {
        if (clusters.size() == 0) {
            throw new IllegalArgumentException("Your Vespa cluster does not have any content clusters " +
                    "declared. Visiting feature is not available.");
        }
        if (! wantedCluster.isPresent()) {
            if (clusters.size() != 1) {
                throw new RestApiException(Response.createErrorResponse(400, "Several clusters exist: " +
                        clusterListToString(clusters) + " you must specify one. ", RestUri.apiErrorCodes.SEVERAL_CLUSTERS));
            }
            return clusters.get(0);
        }

        for (ClusterDef clusterDef : clusters) {
            if (clusterDef.getName().equals(wantedCluster.get())) {
                return clusterDef;
            }
        }
        throw new RestApiException(Response.createErrorResponse(400, "Your vespa cluster contains the content clusters " +
                clusterListToString(clusters) + " not " + wantedCluster.get() + ". Please select a valid vespa cluster.", RestUri.apiErrorCodes.MISSING_CLUSTER));
    }

    // Based on resolveClusterRoute in VdsVisit, protected for testability
    // TODO remove in favor of resolveClusterDef
    protected static String resolveClusterRoute(Optional<String> wantedCluster, List<ClusterDef> clusters) throws RestApiException {
        return clusterDefToRoute(resolveClusterDef(wantedCluster, clusters));
    }

    protected static String clusterDefToRoute(ClusterDef clusterDef) {
        return "[Storage:cluster=" + clusterDef.getName() + ";clusterconfigid=" + clusterDef.getConfigId() + "]";
    }

    private static String clusterListToString(List<ClusterDef> clusters) {
        StringBuilder clusterListString = new StringBuilder();
        clusters.forEach(x -> clusterListString.append(x.getName()).append(" (").append(x.getConfigId()).append("), "));
        return clusterListString.toString();
    }

    private VisitorParameters createVisitorParameters(
            RestUri restUri,
            String documentSelection,
            VisitOptions options)
            throws RestApiException {

        StringBuilder selection = new StringBuilder();

        if (! documentSelection.isEmpty()) {
            // TODO shouldn't selection be wrapped in () itself ?
            selection.append("(").append(documentSelection).append(" and ");
        }
        selection.append(restUri.getDocumentType()).append(" and (id.namespace=='").append(restUri.getNamespace()).append("')");
        if (! documentSelection.isEmpty()) {
            selection.append(")");
        }

        VisitorParameters params = new VisitorParameters(selection.toString());
        // Only return fieldset that is part of the document.
        params.fieldSet(restUri.getDocumentType() + ":[document]");
        params.setMaxBucketsPerVisitor(1);
        params.setMaxPending(32);
        params.setMaxFirstPassHits(1);
        params.setMaxTotalHits(options.wantedDocumentCount
                .map(n -> Math.min(Math.max(n, 1), WANTED_DOCUMENT_COUNT_UPPER_BOUND))
                .orElse(1));
        params.setThrottlePolicy(new StaticThrottlePolicy().setMaxPendingCount(1));
        params.setToTimestamp(0L);
        params.setFromTimestamp(0L);
        params.setSessionTimeoutMs(VISIT_TIMEOUT_MS);

        params.visitInconsistentBuckets(true); // TODO document this as part of consistency doc
        params.setVisitorOrdering(VisitorOrdering.ASCENDING);

        BucketSpaceRoute bucketSpaceRoute = resolveBucketSpaceRoute(options.cluster, restUri.getDocumentType());
        params.setRoute(bucketSpaceRoute.getClusterRoute());
        params.setBucketSpace(bucketSpaceRoute.getBucketSpace());

        params.setTraceLevel(0);
        params.setPriority(DocumentProtocol.Priority.NORMAL_4);
        params.setVisitRemoves(false);

        if (options.continuation.isPresent()) {
            try {
                params.setResumeToken(ContinuationHit.getToken(options.continuation.get()));
            } catch (Exception e) {
                throw new RestApiException(Response.createErrorResponse(500, ExceptionUtils.getStackTrace(e), restUri, RestUri.apiErrorCodes.UNSPECIFIED));
            }
        }
        return params;
    }

}
