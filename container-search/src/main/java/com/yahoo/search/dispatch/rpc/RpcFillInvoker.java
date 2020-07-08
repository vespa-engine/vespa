// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.rpc;

import com.yahoo.collections.ListMap;
import com.yahoo.compress.CompressionType;
import com.yahoo.compress.Compressor;
import com.yahoo.container.protect.Error;
import com.yahoo.data.access.Inspector;
import com.yahoo.data.access.slime.SlimeAdapter;
import com.yahoo.prelude.Location;
import com.yahoo.prelude.fastsearch.DocumentDatabase;
import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.prelude.fastsearch.TimeoutException;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.FillInvoker;
import com.yahoo.search.dispatch.rpc.Client.GetDocsumsResponse;
import com.yahoo.search.query.SessionId;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.BinaryFormat;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link FillInvoker} implementation using RPC
 *
 * @author bratseth
 * @author ollivir
 */
public class RpcFillInvoker extends FillInvoker {
    private static final Logger log = Logger.getLogger(RpcFillInvoker.class.getName());

    private final DocumentDatabase documentDb;
    private final RpcResourcePool resourcePool;
    private GetDocsumsResponseReceiver responseReceiver;

    RpcFillInvoker(RpcResourcePool resourcePool, DocumentDatabase documentDb) {
        this.documentDb = documentDb;
        this.resourcePool = resourcePool;
    }

    @Override
    protected void sendFillRequest(Result result, String summaryClass) {
        ListMap<Integer, FastHit> hitsByNode = hitsByNode(result);
        Query query = result.getQuery();

        CompressionType compression = CompressionType
                .valueOf(query.properties().getString(RpcResourcePool.dispatchCompression, "LZ4").toUpperCase());

        if (query.getTraceLevel() >= 3) {
            query.trace("Sending " + hitsByNode.size() + " summary fetch RPC requests", 3);
            query.trace("RpcSlime: Not resending query during document summary fetching", 3);
        }

        responseReceiver = new GetDocsumsResponseReceiver(hitsByNode.size(), resourcePool.compressor(), result);
        for (Map.Entry<Integer, List<FastHit>> nodeHits : hitsByNode.entrySet()) {
            sendGetDocsumsRequest(nodeHits.getKey(), nodeHits.getValue(), summaryClass, compression, result, responseReceiver);
        }
    }

    @Override
    protected void getFillResults(Result result, String summaryClass) {
        try {
            responseReceiver.processResponses(result.getQuery(), summaryClass, documentDb);
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

    /** Return a map of hits by their search node (partition) id */
    private static ListMap<Integer, FastHit> hitsByNode(Result result) {
        ListMap<Integer, FastHit> hitsByNode = new ListMap<>();
        for (Iterator<Hit> i = result.hits().unorderedDeepIterator(); i.hasNext();) {
            Hit h = i.next();
            if (!(h instanceof FastHit))
                continue;
            FastHit hit = (FastHit) h;

            hitsByNode.put(hit.getDistributionKey(), hit);
        }
        return hitsByNode;
    }

    /** Send a getDocsums request to a node. Responses will be added to the given receiver. */
    private void sendGetDocsumsRequest(int nodeId, List<FastHit> hits, String summaryClass, CompressionType compression,
                                       Result result, GetDocsumsResponseReceiver responseReceiver) {
        Client.NodeConnection node = resourcePool.getConnection(nodeId);
        if (node == null) {
            String error = "Could not fill hits from unknown node " + nodeId;
            responseReceiver.receive(Client.ResponseOrError.fromError(error));
            result.hits().addError(ErrorMessage.createEmptyDocsums(error));
            log.warning("Got hits with partid " + nodeId + ", which is not included in the current dispatch config");
            return;
        }

        Query query = result.getQuery();
        String rankProfile = query.getRanking().getProfile();
        byte[] serializedSlime = BinaryFormat
                .encode(toSlime(rankProfile, summaryClass, query.getModel().getDocumentDb(),
                                query.getSessionId(), query.getRanking().getLocation(), hits));
        double timeoutSeconds = ((double) query.getTimeLeft() - 3.0) / 1000.0;
        Compressor.Compression compressionResult = resourcePool.compress(query, serializedSlime);
        node.getDocsums(hits, compressionResult.type(), serializedSlime.length, compressionResult.data(), responseReceiver, timeoutSeconds);
    }

    static private Slime toSlime(String rankProfile, String summaryClass, String docType, SessionId sessionId, Location location, List<FastHit> hits) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        if (summaryClass != null) {
            root.setString("class", summaryClass);
        }
        if (sessionId != null) {
            root.setData("sessionid", sessionId.asUtf8String().getBytes());
        }
        if (docType != null) {
            root.setString("doctype", docType);
        }
        if (rankProfile != null) {
            root.setString("ranking", rankProfile);
        }
        if (location != null) {
            root.setString("location", location.backendString());
        }
        Cursor gids = root.setArray("gids");
        for (FastHit hit : hits) {
            gids.addData(hit.getRawGlobalId());
        }
        return slime;
    }

    /** Receiver of the responses to a set of getDocsums requests */
    public static class GetDocsumsResponseReceiver {

        private final BlockingQueue<Client.ResponseOrError<GetDocsumsResponse>> responses;
        private final Compressor compressor;
        private final Result result;

        /** Whether we have already logged/notified about an error - to avoid spamming */
        private boolean hasReportedError = false;

        /** The number of responses we should receive (and process) before this is complete */
        private int outstandingResponses;

        GetDocsumsResponseReceiver(int requestCount, Compressor compressor, Result result) {
            this.compressor = compressor;
            responses = new LinkedBlockingQueue<>(requestCount);
            outstandingResponses = requestCount;
            this.result = result;
        }

        /** Called by a thread belonging to the client when a valid response becomes available */
        public void receive(Client.ResponseOrError<GetDocsumsResponse> response) {
            responses.add(response);
        }

        private void throwTimeout() throws TimeoutException {
            throw new TimeoutException("Timed out waiting for summary data. " + outstandingResponses + " responses outstanding.");
        }

        /**
         * Call this from the dispatcher thread to initiate and complete processing of responses.
         * This will block until all responses are available and processed, or to timeout.
         */
        void processResponses(Query query, String summaryClass, DocumentDatabase documentDb) throws TimeoutException {
            try {
                int skippedHits = 0;
                while (outstandingResponses > 0) {
                    long timeLeftMs = query.getTimeLeft();
                    if (timeLeftMs <= 0) {
                        throwTimeout();
                    }
                    Client.ResponseOrError<GetDocsumsResponse> response = responses.poll(timeLeftMs, TimeUnit.MILLISECONDS);
                    if (response == null)
                        throwTimeout();
                    skippedHits += processResponse(response, summaryClass, documentDb);
                    outstandingResponses--;
                }
                if (skippedHits != 0) {
                    result.hits().addError(com.yahoo.search.result.ErrorMessage.createEmptyDocsums("Missing hit summary data for summary " +
                                                                                                   summaryClass + " for " + skippedHits + " hits"));
                }
            }
            catch (InterruptedException e) {
                // TODO: Add error
            }
        }

        private int processResponse(Client.ResponseOrError<GetDocsumsResponse> responseOrError,
                                    String summaryClass,
                                    DocumentDatabase documentDb) {
            if (responseOrError.error().isPresent()) {
                if (hasReportedError) return 0;
                String error = responseOrError.error().get();
                result.hits().addError(ErrorMessage.createBackendCommunicationError(error));
                log.log(Level.WARNING, "Error fetching summary data: "+ error);
            }
            else {
                Client.GetDocsumsResponse response = responseOrError.response().get();
                CompressionType compression = CompressionType.valueOf(response.compression());
                byte[] slimeBytes = compressor.decompress(response.compressedSlimeBytes(), compression, response.uncompressedSize());
                return fill(response.hitsContext(), summaryClass, documentDb, slimeBytes);
            }
            return 0;
        }

        private void addErrors(com.yahoo.slime.Inspector errors) {
            errors.traverse((ArrayTraverser) (int index, com.yahoo.slime.Inspector value) -> {
                int errorCode = ("timeout".equalsIgnoreCase(value.field("type").asString()))
                        ? Error.TIMEOUT.code
                        : Error.UNSPECIFIED.code;
                result.hits().addError(new ErrorMessage(errorCode,
                        value.field("message").asString(), value.field("details").asString()));
            });
        }

        private int fill(List<FastHit> hits, String summaryClass, DocumentDatabase documentDb, byte[] slimeBytes) {
            com.yahoo.slime.Inspector root = BinaryFormat.decode(slimeBytes).get();
            com.yahoo.slime.Inspector errors = root.field("errors");
            boolean hasErrors = errors.valid() && (errors.entries() > 0);
            if (hasErrors) {
                addErrors(errors);
            }

            Inspector summaries = new SlimeAdapter(root.field("docsums"));
            if ( ! summaries.valid())
                return 0; // No summaries; Perhaps we requested a non-existing summary class
            int skippedHits = 0;
            for (int i = 0; i < hits.size(); i++) {
                Inspector summary = summaries.entry(i).field("docsum");
                if (summary.valid()) {
                    hits.get(i).setField(Hit.SDDOCNAME_FIELD, documentDb.getName());
                    hits.get(i).addSummary(documentDb.getDocsumDefinitionSet().getDocsum(summaryClass), summary);
                    hits.get(i).setFilled(summaryClass);
                } else {
                    skippedHits++;
                }
            }
            return skippedHits;
        }

    }
}
