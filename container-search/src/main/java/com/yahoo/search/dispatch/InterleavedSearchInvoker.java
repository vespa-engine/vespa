// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.fs4.QueryPacket;
import com.yahoo.prelude.fastsearch.CacheKey;
import com.yahoo.prelude.fastsearch.VespaBackEndSearcher;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.result.Coverage;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.container.handler.Coverage.DEGRADED_BY_ADAPTIVE_TIMEOUT;
import static com.yahoo.container.handler.Coverage.DEGRADED_BY_TIMEOUT;

/**
 * InterleavedSearchInvoker uses multiple {@link SearchInvoker} objects to interface with content
 * nodes in parallel. Operationally it first sends requests to all contained invokers and then
 * collects the results. The user of this class is responsible for merging the results if needed.
 *
 * @author ollivir
 */
public class InterleavedSearchInvoker extends SearchInvoker implements ResponseMonitor<SearchInvoker> {
    private static final Logger log = Logger.getLogger(InterleavedSearchInvoker.class.getName());

    private final Set<SearchInvoker> invokers;
    private final VespaBackEndSearcher searcher;
    private final SearchCluster searchCluster;
    private final LinkedBlockingQueue<SearchInvoker> availableForProcessing;
    private Query query;

    private boolean adaptiveTimeoutCalculated = false;
    private long adaptiveTimeoutMin = 0;
    private long adaptiveTimeoutMax = 0;
    private long deadline = 0;

    private Result result = null;
    private long answeredDocs = 0;
    private long answeredActiveDocs = 0;
    private long answeredSoonActiveDocs = 0;
    private int askedNodes = 0;
    private int answeredNodes = 0;
    private boolean timedOut = false;

    private boolean trimResult = false;

    public InterleavedSearchInvoker(Collection<SearchInvoker> invokers, VespaBackEndSearcher searcher, SearchCluster searchCluster) {
        super(Optional.empty());
        this.invokers = Collections.newSetFromMap(new IdentityHashMap<>());
        this.invokers.addAll(invokers);
        this.searcher = searcher;
        this.searchCluster = searchCluster;
        this.availableForProcessing = newQueue();
    }

    /**
     * Sends search queries to the contained {@link SearchInvoker} sub-invokers. If the search
     * query has an offset other than zero, it will be reset to zero and the expected hit amount
     * will be adjusted accordingly.
     */
    @Override
    protected void sendSearchRequest(Query query, QueryPacket queryPacket) throws IOException {
        this.query = query;
        invokers.forEach(invoker -> invoker.setMonitor(this));
        deadline = currentTime() + query.getTimeLeft();

        int originalHits = query.getHits();
        int originalOffset = query.getOffset();
        query.setHits(query.getHits() + query.getOffset());
        query.setOffset(0);
        trimResult = originalHits != query.getHits() || originalOffset != query.getOffset();

        for (SearchInvoker invoker : invokers) {
            invoker.sendSearchRequest(query, null);
            askedNodes++;
        }

        query.setHits(originalHits);
        query.setOffset(originalOffset);
    }

    @Override
    protected Result getSearchResult(CacheKey cacheKey, Execution execution) throws IOException {
        long nextTimeout = query.getTimeLeft();
        try {
            while (!invokers.isEmpty() && nextTimeout >= 0) {
                SearchInvoker invoker = availableForProcessing.poll(nextTimeout, TimeUnit.MILLISECONDS);
                if (invoker == null) {
                    if (log.isLoggable(Level.FINE)) {
                        log.fine("Search timed out with " + askedNodes + " requests made, " + answeredNodes + " responses received");
                    }
                    break;
                } else {
                    invokers.remove(invoker);
                    mergeResult(invoker.getSearchResult(cacheKey, execution));
                }
                nextTimeout = nextTimeout();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for search results", e);
        }

        if (result == null) {
            result = new Result(query);
        }
        insertTimeoutErrors();
        result.setCoverage(createCoverage());
        trimResult(execution);
        Result ret = result;
        result = null;
        return ret;
    }

    private void trimResult(Execution execution) {
        if (trimResult) {
            if (result.getHitOrderer() != null) {
                searcher.fill(result, Execution.ATTRIBUTEPREFETCH, execution);
            }

            result.hits().trim(query.getOffset(), query.getHits());
        }
    }

    private void insertTimeoutErrors() {
        for (SearchInvoker invoker : invokers) {
            Optional<Integer> dk = invoker.distributionKey();
            String message;
            if (dk.isPresent()) {
                message = "Backend communication timeout on node with distribution-key " + dk.get();
            } else {
                message = "Backend communication timeout";
            }
            result.hits().addError(ErrorMessage.createBackendCommunicationError(message));
            invoker.getErrorCoverage().ifPresent(this::collectCoverage);
            timedOut = true;
        }
    }

    private long nextTimeout() {
        DispatchConfig config = searchCluster.dispatchConfig();
        double minimumCoverage = config.minSearchCoverage();

        if (askedNodes == answeredNodes || minimumCoverage >= 100.0) {
            return query.getTimeLeft();
        }
        int minimumResponses = (int) Math.ceil(askedNodes * minimumCoverage / 100.0);

        if (answeredNodes < minimumResponses) {
            return query.getTimeLeft();
        }

        long timeLeft = query.getTimeLeft();
        if (!adaptiveTimeoutCalculated) {
            adaptiveTimeoutMin = (long) (timeLeft * config.minWaitAfterCoverageFactor());
            adaptiveTimeoutMax = (long) (timeLeft * config.maxWaitAfterCoverageFactor());
            adaptiveTimeoutCalculated = true;
        }

        long now = currentTime();
        int pendingQueries = askedNodes - answeredNodes;
        double missWidth = ((100.0 - config.minSearchCoverage()) * askedNodes) / 100.0 - 1.0;
        double slopedWait = adaptiveTimeoutMin;
        if (pendingQueries > 1 && missWidth > 0.0) {
            slopedWait += ((adaptiveTimeoutMax - adaptiveTimeoutMin) * (pendingQueries - 1)) / missWidth;
        }
        long nextAdaptive = (long) slopedWait;
        if (now + nextAdaptive >= deadline) {
            return deadline - now;
        }
        deadline = now + nextAdaptive;

        return nextAdaptive;
    }

    private void mergeResult(Result partialResult) {
        collectCoverage(partialResult.getCoverage(true));

        if (result == null) {
            result = partialResult;
            return;
        }

        result.mergeWith(partialResult);
        result.hits().addAll(partialResult.hits().asUnorderedHits());
    }

    private void collectCoverage(Coverage source) {
        answeredDocs += source.getDocs();
        answeredActiveDocs += source.getActive();
        answeredSoonActiveDocs += source.getSoonActive();
        answeredNodes++;
    }

    private Coverage createCoverage() {
        adjustDegradedCoverage();
        Coverage coverage = new Coverage(answeredDocs, answeredActiveDocs, answeredNodes, 1);
        coverage.setNodesTried(askedNodes);
        coverage.setSoonActive(answeredSoonActiveDocs);
        if (timedOut) {
            coverage.setDegradedReason(adaptiveTimeoutCalculated ? DEGRADED_BY_ADAPTIVE_TIMEOUT : DEGRADED_BY_TIMEOUT);
        }
        return coverage;
    }

    private void adjustDegradedCoverage() {
        if (askedNodes == answeredNodes) {
            return;
        }
        int notAnswered = askedNodes - answeredNodes;

        if (adaptiveTimeoutCalculated) {
            answeredActiveDocs += (notAnswered * answeredActiveDocs / answeredNodes);
            answeredSoonActiveDocs += (notAnswered * answeredSoonActiveDocs / answeredNodes);
        } else {
            if (askedNodes > answeredNodes) {
                int searchableCopies = (int) searchCluster.dispatchConfig().searchableCopies();
                int missingNodes = notAnswered - (searchableCopies - 1);
                if (answeredNodes > 0) {
                    answeredActiveDocs += (missingNodes * answeredActiveDocs / answeredNodes);
                    answeredSoonActiveDocs += (missingNodes * answeredSoonActiveDocs / answeredNodes);
                    timedOut = true;
                }
            }
        }
    }

    @Override
    protected void release() {
        if (!invokers.isEmpty()) {
            invokers.forEach(SearchInvoker::close);
            invokers.clear();
        }
    }

    @Override
    public void responseAvailable(SearchInvoker from) {
        if (availableForProcessing != null) {
            availableForProcessing.add(from);
        }
    }

    @Override
    protected void setMonitor(ResponseMonitor<SearchInvoker> monitor) {
        // never to be called
    }

    // For overriding in tests
    protected long currentTime() {
        return System.currentTimeMillis();
    }

    // For overriding in tests
    protected LinkedBlockingQueue<SearchInvoker> newQueue() {
        return new LinkedBlockingQueue<>();
    }
}
