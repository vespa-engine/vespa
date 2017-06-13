// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.storage.searcher;

import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.feedhandler.NullFeedMetric;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.documentapi.*;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.feedapi.FeedContext;
import com.yahoo.feedapi.MessagePropertyProcessor;
import com.yahoo.messagebus.StaticThrottlePolicy;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vdslib.VisitorOrdering;
import com.yahoo.vespaclient.ClusterDef;
import com.yahoo.vespaclient.config.FeederConfig;

/**
 * A searcher that allows you to iterate through a storage cluster using visiting.
 */
public class VisitSearcher extends Searcher {

    public static final String VISITOR_CONTINUATION_TOKEN_FIELDNAME = "visitorContinuationToken";
    FeedContext context;

    public VisitSearcher(FeederConfig feederConfig, 
                         LoadTypeConfig loadTypeConfig,
                         DocumentmanagerConfig documentmanagerConfig,
                         SlobroksConfig slobroksConfig,
                         ClusterListConfig clusterListConfig) throws Exception {
        this(FeedContext.getInstance(feederConfig, loadTypeConfig, documentmanagerConfig, 
                                     slobroksConfig, clusterListConfig, new NullFeedMetric()));
    }

    VisitSearcher(FeedContext context) throws Exception {
        this.context = context;
    }

    class HitDataHandler extends DumpVisitorDataHandler {
        private final Result result;
        private final boolean populateHits;
        private final Object monitor = new Object();

        HitDataHandler(Result result, boolean populateHits) {
            this.result = result;
            this.populateHits = populateHits;
        }

        @Override
        public void onDocument(Document document, long l) {
            final DocumentHit hit = new DocumentHit(document, 0);
            if (populateHits) {
                hit.populateHitFields();
            }
            synchronized (monitor) {
                result.hits().add(hit);
            }
        }

        @Override
        public void onRemove(DocumentId documentId) {
            final DocumentRemoveHit hit = new DocumentRemoveHit(documentId);
            synchronized (monitor) {
                result.hits().add(hit);
            }
        }
    }

    public VisitorParameters getVisitorParameters(Query query, Result result) throws Exception {
        String documentSelection = query.properties().getString("visit.selection");
        if (documentSelection == null) {
            documentSelection = "";
        }

        VisitorParameters params = new VisitorParameters(documentSelection);
        params.setMaxBucketsPerVisitor(query.properties().getInteger("visit.maxBucketsPerVisitor", 1));
        params.setMaxPending(query.properties().getInteger("visit.maxPendingMessagesPerVisitor", 32));
        params.setMaxFirstPassHits(query.properties().getInteger("visit.approxMaxDocs", 1));
        params.setMaxTotalHits(query.properties().getInteger("visit.approxMaxDocs", 1));
        params.setThrottlePolicy(new StaticThrottlePolicy().setMaxPendingCount(
                query.properties().getInteger("visit.maxPendingVisitors", 1)));
        params.setToTimestamp(query.properties().getLong("visit.toTimestamp", 0L));
        params.setFromTimestamp(query.properties().getLong("visit.fromTimestamp", 0L));

        String pri = query.properties().getString("visit.priority");
        if (pri != null) {
            params.setPriority(DocumentProtocol.Priority.valueOf(pri));
        }

        if (query.properties().getBoolean("visit.visitInconsistentBuckets")) {
            params.visitInconsistentBuckets(true);
        }

        String ordering = query.properties().getString("visit.order");
        if (!"ascending".equalsIgnoreCase(ordering)) {
            params.setVisitorOrdering(VisitorOrdering.ASCENDING);
        } else {
            params.setVisitorOrdering(VisitorOrdering.DESCENDING);
        }

        String remoteCluster = query.properties().getString("visit.dataHandler");
        if (remoteCluster != null) {
            params.setRemoteDataHandler(remoteCluster);
        } else {
            params.setLocalDataHandler(new HitDataHandler(
                    result, query.properties().getBoolean("populatehitfields", false)));
        }

        String fieldSet = query.properties().getString("visit.fieldSet");
        if (fieldSet != null) {
            params.fieldSet(fieldSet);
        }

        String continuation = query.properties().getString("visit.continuation");
        if (continuation != null) {
            params.setResumeToken(ContinuationHit.getToken(continuation));
        }

        params.setVisitRemoves(query.properties().getBoolean("visit.visitRemoves"));

        MessagePropertyProcessor.PropertySetter propertySetter;
        propertySetter = context.getPropertyProcessor().buildPropertySetter(query.getHttpRequest());

        propertySetter.process(params);

        if (context.getClusterList().getStorageClusters().size() == 0) {
            throw new IllegalArgumentException("No content clusters have been defined");
        }

        String route = query.properties().getString("visit.cluster");
        ClusterDef found = null;
        if (route != null) {
            String names = "";
            for (ClusterDef c : context.getClusterList().getStorageClusters()) {
                if (c.getName().equals(route)) {
                    found = c;
                }
                if (!names.isEmpty()) {
                    names += ", ";
                }
                names += c.getName();
            }
            if (found == null) {
                throw new IllegalArgumentException("Your vespa cluster contains the storage clusters " + names + ", not " + route + ". Please select a valid vespa cluster.");
            }
        } else if (context.getClusterList().getStorageClusters().size() == 1) {
            found = context.getClusterList().getStorageClusters().get(0);
        } else {
            throw new IllegalArgumentException("Multiple content clusters are defined, select one using the \"visit.cluster\" option");
        }

        params.setRoute("[Storage:cluster=" + found.getName() + ";clusterconfigid=" + found.getConfigId() + "]");
        return params;
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result result = execution.search(query);

        VisitorParameters parameters;

        try {
            parameters = getVisitorParameters(query, result);
        } catch (Exception e) {
            return new Result(query, ErrorMessage.createBadRequest("Illegal parameters: " + e.toString()));
        }

        if (parameters != null) {
            VisitorSession session = context.getSessionFactory().createVisitorSession(parameters);

            try {
                if (!session.waitUntilDone(query.getTimeout())) {
                    return new Result(query, ErrorMessage.createTimeout("Visitor timed out"));
                }

                ProgressToken token = session.getProgress();
                if (!token.isFinished()) {
                    final ContinuationHit continuation = new ContinuationHit(token);
                    result.hits().setField(VISITOR_CONTINUATION_TOKEN_FIELDNAME, continuation.getValue());
                }
            } catch (InterruptedException e) {
            } finally {
                session.destroy();
            }
        }

        GetSearcher.setOutputFormat(query, result);
        query.setHits(result.hits().size());
        return result;
    }

}
