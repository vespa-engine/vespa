// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.TestAndSetCondition;
import com.yahoo.document.json.JsonWriter;
import com.yahoo.document.DocumentPut;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentAccessException;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.SyncSession;
import com.yahoo.documentapi.VisitorControlHandler;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.VisitorSession;
import com.yahoo.documentapi.messagebus.MessageBusSyncSession;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.StaticThrottlePolicy;
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
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Sends operations to messagebus via document api.
 *
 * @author dybis 
 */
public class OperationHandlerImpl implements OperationHandler {

    public static final int VISIT_TIMEOUT_MS = 120000;
    private final DocumentAccess documentAccess;

    private static final class SyncSessionFactory extends ResourceFactory<SyncSession> {
        private final DocumentAccess documentAccess;
        SyncSessionFactory(DocumentAccess documentAccess) {
            this.documentAccess = documentAccess;
        }
        @Override
        public SyncSession create() {
            return documentAccess.createSyncSession(new SyncParameters());
        }
    }

    private final ConcurrentResourcePool<SyncSession> syncSessions;

    public OperationHandlerImpl(DocumentAccess documentAccess) {
        this.documentAccess = documentAccess;
        syncSessions = new ConcurrentResourcePool<>(new SyncSessionFactory(documentAccess));
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

    private static int getHTTPStatusCode(DocumentAccessException documentException) {
        if (documentException.getErrorCodes().size() == 1 && documentException.getErrorCodes().contains(DocumentProtocol.ERROR_NO_SPACE)) {
            return HTTP_STATUS_INSUFFICIENT_STORAGE;
        }
        if (documentException.hasConditionNotMetError()) {
            return HTTP_PRE_CONDIDTION_FAILED;
        }
        return HTTP_STATUS_BAD_REQUEST;
    }

    private static Response createErrorResponse(DocumentAccessException documentException, RestUri restUri) {
        if (documentException.hasConditionNotMetError()) {
            return Response.createErrorResponse(getHTTPStatusCode(documentException), "Condition did not match document.",
                    restUri, RestUri.apiErrorCodes.DOCUMENT_CONDITION_NOT_MET);
        }
        return Response.createErrorResponse(getHTTPStatusCode(documentException), documentException.getMessage(), restUri,
                RestUri.apiErrorCodes.DOCUMENT_EXCPETION);
    }

    @Override
    public VisitResult visit(
            RestUri restUri,
            String documentSelection,
            Optional<String> cluster,
            Optional<String> continuation) throws RestApiException {

        VisitorParameters visitorParameters = createVisitorParameters(restUri, documentSelection, cluster, continuation);

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

    private VisitResult doVisit(
            VisitorControlHandler visitorControlHandler,
            LocalDataVisitorHandler localDataVisitorHandler,
            RestUri restUri) throws RestApiException {
        try {
            if (! visitorControlHandler.waitUntilDone(VISIT_TIMEOUT_MS)) {
                throw new RestApiException(Response.createErrorResponse(500, "Timed out", restUri, RestUri.apiErrorCodes.TIME_OUT));
            }
            if (visitorControlHandler.getResult().code != VisitorControlHandler.CompletionCode.SUCCESS) {
                throw new RestApiException(Response.createErrorResponse(400, visitorControlHandler.getResult().toString(), RestUri.apiErrorCodes.VISITOR_ERROR));
            }
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
        try {
            DocumentPut put = new DocumentPut(data.getDocument());
            put.setCondition(data.getCondition());
            setRoute(syncSession, route);
            syncSession.put(put);
        } catch (DocumentAccessException documentException) {
            throw new RestApiException(createErrorResponse(documentException, restUri));
        } catch (Exception e) {
            throw new RestApiException(Response.createErrorResponse(500, ExceptionUtils.getStackTrace(e), restUri, RestUri.apiErrorCodes.INTERNAL_EXCEPTION));
        } finally {
            syncSessions.free(syncSession);
        }
    }

    @Override
    public void update(RestUri restUri, VespaXMLFeedReader.Operation data, Optional<String> route) throws RestApiException {
        SyncSession syncSession = syncSessions.alloc();
        setRoute(syncSession, route);
        try {
            syncSession.update(data.getDocumentUpdate());
        } catch (DocumentAccessException documentException) {
            throw new RestApiException(createErrorResponse(documentException, restUri));
        } catch (Exception e) {
            throw new RestApiException(Response.createErrorResponse(500, ExceptionUtils.getStackTrace(e), restUri, RestUri.apiErrorCodes.INTERNAL_EXCEPTION));
        } finally {
            syncSessions.free(syncSession);
        }
    }

    @Override
    public void delete(RestUri restUri, String condition, Optional<String> route) throws RestApiException {
        SyncSession syncSession = syncSessions.alloc();
        setRoute(syncSession, route);
        try {
            DocumentId id = new DocumentId(restUri.generateFullId());
            DocumentRemove documentRemove = new DocumentRemove(id);
            if (condition != null && ! condition.isEmpty()) {
                documentRemove.setCondition(new TestAndSetCondition(condition));
            }
            syncSession.remove(documentRemove);
        } catch (DocumentAccessException documentException) {
            if (documentException.hasConditionNotMetError()) {
                throw new RestApiException(Response.createErrorResponse(412, "Condition not met: " + documentException.getMessage(),
                        restUri, RestUri.apiErrorCodes.DOCUMENT_CONDITION_NOT_MET));
            }
            throw new RestApiException(Response.createErrorResponse(400, documentException.getMessage(), restUri, RestUri.apiErrorCodes.DOCUMENT_EXCPETION));
        } catch (Exception e) {
            throw new RestApiException(Response.createErrorResponse(500, ExceptionUtils.getStackTrace(e), restUri, RestUri.apiErrorCodes.UNSPECIFIED));
        } finally {
            syncSessions.free(syncSession);
        }
    }

    @Override
    public Optional<String> get(RestUri restUri) throws RestApiException {
        SyncSession syncSession = syncSessions.alloc();
        setRoute(syncSession, Optional.empty());
        try {
            DocumentId id = new DocumentId(restUri.generateFullId());
            final Document document = syncSession.get(id);
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

    private static String resolveClusterRoute(Optional<String> wantedCluster) throws RestApiException {
        List<ClusterDef> clusters = new ClusterList("client").getStorageClusters();
        return resolveClusterRoute(wantedCluster, clusters);
    }

    // Based on resolveClusterRoute in VdsVisit, protected for testability
    protected static String resolveClusterRoute(Optional<String> wantedCluster, List<ClusterDef> clusters) throws RestApiException {
        if (clusters.size() == 0) {
            throw new IllegalArgumentException("Your Vespa cluster does not have any content clusters " +
                    "declared. Visiting feature is not available.");
        }
        if (! wantedCluster.isPresent()) {
            if (clusters.size() != 1) {
                new RestApiException(Response.createErrorResponse(400, "Several clusters exist: " +
                        clusterListToString(clusters) + " you must specify one.. ", RestUri.apiErrorCodes.SEVERAL_CLUSTERS));
            }
            return clusterDefToRoute(clusters.get(0));
        }

        for (ClusterDef clusterDef : clusters) {
            if (clusterDef.getName().equals(wantedCluster.get())) {
                return clusterDefToRoute(clusterDef);
            }
        }
        throw new RestApiException(Response.createErrorResponse(400, "Your vespa cluster contains the content clusters " +
                clusterListToString(clusters) + " not " + wantedCluster.get() + ". Please select a valid vespa cluster.", RestUri.apiErrorCodes.MISSING_CLUSTER));

    }

    private static String clusterDefToRoute(ClusterDef clusterDef) {
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
            Optional<String> clusterName,
            Optional<String> continuation)
            throws RestApiException {

        StringBuilder selection = new StringBuilder();

        if (! documentSelection.isEmpty()) {
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
        params.setMaxTotalHits(10);
        params.setThrottlePolicy(new StaticThrottlePolicy().setMaxPendingCount(1));
        params.setToTimestamp(0L);
        params.setFromTimestamp(0L);

        params.visitInconsistentBuckets(true);
        params.setVisitorOrdering(VisitorOrdering.ASCENDING);

        params.setRoute(resolveClusterRoute(clusterName));

        params.setTraceLevel(0);
        params.setPriority(DocumentProtocol.Priority.NORMAL_4);
        params.setVisitRemoves(false);

        if (continuation.isPresent()) {
            try {
                params.setResumeToken(ContinuationHit.getToken(continuation.get()));
            } catch (Exception e) {
                throw new RestApiException(Response.createErrorResponse(500, ExceptionUtils.getStackTrace(e), restUri, RestUri.apiErrorCodes.UNSPECIFIED));
            }
        }
        return params;
    }

}
