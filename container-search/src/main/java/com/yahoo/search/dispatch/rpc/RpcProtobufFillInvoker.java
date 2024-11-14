// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import ai.vespa.searchlib.searchprotocol.protobuf.SearchProtocol;
import com.google.protobuf.InvalidProtocolBufferException;
import com.yahoo.collections.ListMap;
import com.yahoo.compress.Compressor;
import com.yahoo.container.protect.Error;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.TimeoutException;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.Dispatcher;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.rpc.Client.ProtobufResponse;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.BinaryFormat;
import com.yahoo.slime.BinaryView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link FillInvoker} implementation using Protobuf over JRT
 *
 * @author bratseth
 * @author ollivir
 */
public class RpcProtobufFillInvoker extends FillInvoker {

    private static final String RPC_METHOD = "vespa.searchprotocol.getDocsums";

    private static final Logger log = Logger.getLogger(RpcProtobufFillInvoker.class.getName());

    enum DecodePolicy {EAGER, ONDEMAND}

    private final DocumentDatabase documentDb;
    private final RpcConnectionPool resourcePool;
    private boolean summaryNeedsQuery;
    private final String serverId;
    private final CompressPayload compressor;
    private final DecodePolicy decodePolicy;

    private record ResponseAndHits(Client.ResponseOrError<ProtobufResponse> response, List<FastHit> hits) {}

    private BlockingQueue<ResponseAndHits> responses;

    /** Whether we have already logged/notified about an error - to avoid spamming */
    private boolean hasReportedError = false;

    /** The number of responses we should receive (and process) before this is complete */
    private int outstandingResponses;
    private int numOkFilledHits = 0;
    private int numHitsToFill = 0;

    RpcProtobufFillInvoker(RpcConnectionPool resourcePool, CompressPayload compressor, DocumentDatabase documentDb,
                           String serverId, DecodePolicy decodePolicy, boolean summaryNeedsQuery) {
        this.documentDb = documentDb;
        this.resourcePool = resourcePool;
        this.serverId = serverId;
        this.summaryNeedsQuery = summaryNeedsQuery;
        this.compressor = compressor;
        this.decodePolicy = decodePolicy;
    }

    @Override
    protected void sendFillRequest(Result result, String summaryClass) {
        ListMap<Integer, FastHit> hitsByNode = hitsByNode(result);
        int queueSize = Math.max(hitsByNode.size(), resourcePool.knownNodeIds().size());
        responses = new LinkedBlockingQueue<>(queueSize);
        sendFillRequestByNode(result, summaryClass, hitsByNode);
    }

    void sendFillRequestByNode(Result result, String summaryClass, ListMap<Integer, FastHit> hitsByNode) {
        result.getQuery().trace(false, 5, "Sending ", hitsByNode.size(), " summary fetch requests with jrt/protobuf");

        outstandingResponses = hitsByNode.size();

        var timeout = TimeoutHelper.calculateTimeout(result.getQuery());
        if (timeout.timedOut()) {
            // Need to produce an error response her in case of JVM system clock being adjusted
            // Timeout mechanism relies on System.currentTimeMillis(), not System.nanoTime() :(
            hitsByNode.forEach((nodeId, hits) ->
                    receive(Client.ResponseOrError.fromTimeoutError("Timed out prior to sending docsum request to " + nodeId), hits));
            return;
        }
        var builder = ProtobufSerialization.createDocsumRequestBuilder(
                result.getQuery(), serverId, summaryClass, result.getQuery().getPresentation().getSummaryFields(), summaryNeedsQuery, timeout.request());
        hitsByNode.forEach((nodeId, hits) -> {
            var payload = ProtobufSerialization.serializeDocsumRequest(builder, hits);
            sendDocsumsRequest(nodeId, hits, payload, result, timeout.client());
        });
    }

    @Override
    protected void getFillResults(Result result, String summaryClass) {
        try {
            processResponses(result, summaryClass);
            result.hits().setSorted(false);
            result.analyzeHits();
        } catch (TimeoutException e) {
            result.hits().addError(ErrorMessage.createTimeout("Summary data is incomplete: " + e.getMessage()));
        }
    }

    @Override
    protected void release() {
        // nothing to release
    }

    /** Called by a thread belonging to the client when a valid response becomes available */
    public void receive(Client.ResponseOrError<ProtobufResponse> response, List<FastHit> hitsContext) {
        responses.add(new ResponseAndHits(response, hitsContext));
    }

    /** Return a map of hits by their search node (partition) id */
    private final ListMap<Integer, FastHit> hitsByNode(Result result) {
        ListMap<Integer, FastHit> hitsByNode = new ListMap<>();
        for (Hit hit : (Iterable<Hit>) result.hits()::unorderedDeepIterator) {
            if (hit instanceof FastHit fastHit) {
                ++numHitsToFill;
                hitsByNode.put(fastHit.getDistributionKey(), fastHit);
            }
        }
        return hitsByNode;
    }

    /** Send a docsums request to a node. Responses will be added to the given receiver. */
    private void sendDocsumsRequest(int nodeId, List<FastHit> hits, byte[] payload, Result result,
                                    double clientTimeout) {
        Client.NodeConnection node = resourcePool.getConnection(nodeId);
        if (node == null) {
            String error = "Could not fill hits from unknown node " + nodeId;
            receive(Client.ResponseOrError.fromError(error), hits);
            result.hits().addError(ErrorMessage.createEmptyDocsums(error));
            log.warning("Got hits with node id " + nodeId + ", which is not included in the current dispatch config");
            return;
        }
        Query query = result.getQuery();
        Compressor.Compression compressionResult = compressor.compress(query, payload);
        node.request(RPC_METHOD, compressionResult.type(), payload.length, compressionResult.data(),
                roe -> receive(roe, hits), clientTimeout);
    }

    private ResponseAndHits getNextResponse(long timeLeftMs) throws InterruptedException {
        if (timeLeftMs <= 0) {
            return null;
        }
        var responseAndHits = responses.poll(timeLeftMs, TimeUnit.MILLISECONDS);
        if (responseAndHits == null || responseAndHits.response().timeout()) {
            return null;
        }
        return responseAndHits;
    }

    private void processResponses(Result result, String summaryClass) throws TimeoutException {
        try {
            List<FastHit> skippedHits = new ArrayList<>();
            while (outstandingResponses > 0) {
                var responseAndHits = getNextResponse(result.getQuery().getTimeLeft());
                if (responseAndHits == null) {
                    throwTimeout();
                }
                skippedHits.addAll(processOneResponse(result, responseAndHits, summaryClass, false));
                outstandingResponses--;
            }
            if (skippedHits.isEmpty()) {
                // all done OK
                return;
            }
            maybeRetry(skippedHits, result, summaryClass);
            if (! skippedHits.isEmpty()) {
                result.hits().addError(ErrorMessage
                        .createEmptyDocsums("Missing hit summary data for summary " + summaryClass + " for " + skippedHits + " hits"));
            }
        } catch (InterruptedException e) {
            // TODO: Add error
        }
    }

    private List<FastHit> processOneResponse(
            Result result,
            ResponseAndHits responseAndHits,
            String summaryClass,
            boolean ignoreErrors)
    {
        var responseOrError = responseAndHits.response();
        if (responseOrError.error().isPresent()) {
            if (hasReportedError || ignoreErrors) {
                return List.of();
            }
            String error = responseOrError.error().get();
            result.hits().addError(ErrorMessage.createBackendCommunicationError(error));
            log.log(Level.WARNING, "Error fetching summary data: " + error);
            hasReportedError = true;
        } else {
            Client.ProtobufResponse response = responseOrError.response().get();
            byte[] responseBytes = compressor.decompress(response);
            return fill(result, responseAndHits.hits(), summaryClass, responseBytes, ignoreErrors);
        }
        return List.of();
    }

    private void addErrors(Result result, com.yahoo.slime.Inspector errors) {
        errors.traverse((ArrayTraverser) (index, value) -> {
            int errorCode = ("timeout".equalsIgnoreCase(value.field("type").asString())) ? Error.TIMEOUT.code : Error.UNSPECIFIED.code;
            result.hits().addError(new ErrorMessage(errorCode, value.field("message").asString(), value.field("details").asString()));
        });
    }

    private void convertErrorsFromDocsumReply(Result target, List<SearchProtocol.Error> errors) {
        for (var error : errors) {
            target.hits().addError(ErrorMessage.createDocsumReplyError(error.getMessage()));
        }
    }

    private List<FastHit> fill(Result result, List<FastHit> hits, String summaryClass, byte[] payload, boolean ignoreErrors) {
        try {
            var protobuf = SearchProtocol.DocsumReply.parseFrom(payload);
            var root = (decodePolicy == DecodePolicy.ONDEMAND)
                    ? BinaryView.inspect(protobuf.getSlimeSummaries().toByteArray())
                    : BinaryFormat.decode(protobuf.getSlimeSummaries().toByteArray()).get();
            if (! ignoreErrors) {
                var errors = root.field("errors");
                boolean hasErrors = errors.valid() && (errors.entries() > 0);
                if (hasErrors) {
                    addErrors(result, errors);
                }
                convertErrorsFromDocsumReply(result, protobuf.getErrorsList());
            }
            Inspector summaries = new SlimeAdapter(root.field("docsums"));
            if (!summaries.valid()) {
                return List.of(); // No summaries; Perhaps we requested a non-existing summary class
            }
            List<FastHit> skippedHits = new ArrayList<>();
            for (int i = 0; i < hits.size(); i++) {
                Inspector summary = summaries.entry(i).field("docsum");
                FastHit hit = hits.get(i);
                if (summary.valid() && ! hit.isFilled(summaryClass)) {
                    hit.setField(Hit.SDDOCNAME_FIELD, documentDb.schema().name());
                    hit.addSummary(documentDb.getDocsumDefinitionSet().getDocsum(summaryClass), summary);
                    hit.setFilled(summaryClass);
                    ++numOkFilledHits;
                } else {
                    skippedHits.add(hit);
                }
            }
            return skippedHits;
        } catch (InvalidProtocolBufferException ex) {
            if (! ignoreErrors) {
                log.log(Level.WARNING, "Invalid response to docsum request", ex);
                result.hits().addError(ErrorMessage.createInternalServerError("Invalid response to docsum request from backend"));
            }
        }
        return List.of();
    }

    private void throwTimeout() throws TimeoutException {
        throw new TimeoutException("Timed out waiting for summary data. " + outstandingResponses + " responses outstanding.");
    }

    /*
     * The content layer may return some empty docsums when redistribution is in progress,
     * and in that case the document should be present on some other node, and we should
     * be able to get the docsum from that node if we retry.  But we don't know where
     * that would be, so we need to try all possible nodes.
     * To avoid overloading the content layer, we only retry if the number of skipped hits
     * is below a tunable limit, and if the ratio of failed to ok hits is below another
     * tunable limit (if too much failed on first try, it's likely not helpful to retry).
     */
    private void maybeRetry(List<FastHit> skippedHits, Result result, String summaryClass) throws InterruptedException {
        int numSkipped = skippedHits.size();
        var query = result.getQuery();
        double absoluteRetryLimit = query.properties().getInteger(Dispatcher.docsumRetryLimit, 10);
        double retryLimitFactor = query.properties().getDouble(Dispatcher.docsumRetryFactor, 0.5);
        double retryLimit = Math.min(absoluteRetryLimit, retryLimitFactor * numHitsToFill);
        if (numSkipped < retryLimit) {
            result.getQuery().trace(false, 1, "Retry summary fetching for " + numSkipped + " empty docsums (of " + numHitsToFill + " hits)");
            ListMap<Integer, FastHit> retryMap = new ListMap<>();
            for (Integer nodeId : resourcePool.knownNodeIds()) {
                for (var hit : skippedHits) {
                    if (hit.getDistributionKey() != nodeId) {
                        retryMap.put(nodeId, hit);
                    }
                }
            }
            // no retry if there is only one node
            if (retryMap.size() > 0) {
                if (shouldLogRetry()) {
                    log.log(Level.WARNING, "Retry docsum fetch for " + numSkipped + " hits (" + numOkFilledHits + " ok hits)");
                }
                summaryNeedsQuery = true;
                sendFillRequestByNode(result, summaryClass, retryMap);
                while (outstandingResponses > 0 && numOkFilledHits < numHitsToFill) {
                    var responseAndHits = getNextResponse(query.getTimeLeft());
                    if (responseAndHits == null) {
                        if (shouldLogRetryTimeout()) {
                            log.log(Level.WARNING, "Timed out waiting for summary data. " + outstandingResponses + " responses outstanding.");
                        }
                        break;
                    }
                    processOneResponse(result, responseAndHits, summaryClass, true);
                    outstandingResponses--;
                }
                skippedHits.removeIf(hit -> hit.isFilled(summaryClass));
            }
        } else {
            result.getQuery().trace(false, 1, "Summary fetching got " + numSkipped + " empty docsums (of " + numHitsToFill + " hits), no retry");
            if (shouldLogNoRetry()) {
                log.log(Level.WARNING, "Docsum fetch failed for " + numSkipped + " hits (" + numOkFilledHits + " ok hits), no retry");
            }
        }
    }

    private static boolean shouldLogForCount(int count) {
        if (count < 100) return true;
        if (count < 1000) return (count % 100) == 0;
        if (count < 100000) return (count % 1000) == 0;
        return (count % 10000) == 0;
    }
    private static final AtomicInteger retryCounter = new AtomicInteger();
    private static final AtomicInteger noRetryCounter = new AtomicInteger();
    private static final AtomicInteger retryTimeoutCounter = new AtomicInteger();
    private static boolean shouldLogRetry() {
        int count = retryCounter.getAndAdd(1);
        return shouldLogForCount(count);
    }
    private static boolean shouldLogNoRetry() {
        int count = noRetryCounter.getAndAdd(1);
        return shouldLogForCount(count);
    }
    private static boolean shouldLogRetryTimeout() {
        int count = retryTimeoutCounter.getAndAdd(1);
        return shouldLogForCount(count);
    }

}
