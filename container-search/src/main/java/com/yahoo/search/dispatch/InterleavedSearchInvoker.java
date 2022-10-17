// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.concurrent.Timer;
import com.yahoo.prelude.fastsearch.GroupingListHit;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.dispatch.searchcluster.Group;
import com.yahoo.search.dispatch.searchcluster.SearchCluster;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.vespa.config.search.DispatchConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * InterleavedSearchInvoker uses multiple {@link SearchInvoker} objects to interface with content
 * nodes in parallel. Operationally it first sends requests to all contained invokers and then
 * collects the results. The user of this class is responsible for merging the results if needed.
 *
 * @author ollivir
 */
public class InterleavedSearchInvoker extends SearchInvoker implements ResponseMonitor<SearchInvoker> {

    private static final Logger log = Logger.getLogger(InterleavedSearchInvoker.class.getName());

    private final Timer timer;
    private final Set<SearchInvoker> invokers;
    private final SearchCluster searchCluster;
    private final Group group;
    private final LinkedBlockingQueue<SearchInvoker> availableForProcessing;
    private final Set<Integer> alreadyFailedNodes;
    private final CoverageAggregator coverageAggregator;
    private Query query;

    private TimeoutHandler timeoutHandler;
    public InterleavedSearchInvoker(Timer timer, Collection<SearchInvoker> invokers,
                                    SearchCluster searchCluster,
                                    Group group,
                                    Set<Integer> alreadyFailedNodes) {
        super(Optional.empty());
        this.timer = timer;
        this.invokers = Collections.newSetFromMap(new IdentityHashMap<>());
        this.invokers.addAll(invokers);
        this.searchCluster = searchCluster;
        this.group = group;
        this.availableForProcessing = newQueue();
        this.alreadyFailedNodes = alreadyFailedNodes;
        coverageAggregator = new CoverageAggregator(invokers.size());
    }

    private TimeoutHandler createTimeoutHandler(DispatchConfig config, int askedNodes, Query query) {
        return (config.minSearchCoverage() < 100.0D)
                ? new AdaptiveTimeoutHandler(timer, config, askedNodes, query)
                : new SimpleTimeoutHandler(query);
    }

    /**
     * Sends search queries to the contained {@link SearchInvoker} sub-invokers. If the search
     * query has an offset other than zero, it will be reset to zero and the expected hit amount
     * will be adjusted accordingly.
     */
    @Override
    protected Object sendSearchRequest(Query query, Object unusedContext) throws IOException {
        this.query = query;
        invokers.forEach(invoker -> invoker.setMonitor(this));

        int originalHits = query.getHits();
        int originalOffset = query.getOffset();
        int neededHits = originalHits + originalOffset;
        int q = neededHits;
        if (group.isBalanced() && !group.isSparse()) {
            Double topkProbabilityOverrride = query.properties().getDouble(Dispatcher.topKProbability);
            q = (topkProbabilityOverrride != null)
                    ? searchCluster.estimateHitsToFetch(neededHits, invokers.size(), topkProbabilityOverrride)
                    : searchCluster.estimateHitsToFetch(neededHits, invokers.size());
        }
        query.setHits(q);
        query.setOffset(0);

        Object context = null;
        for (SearchInvoker invoker : invokers) {
            context = invoker.sendSearchRequest(query, context);
        }
        timeoutHandler = createTimeoutHandler(searchCluster.dispatchConfig(), invokers.size(), query);

        query.setHits(originalHits);
        query.setOffset(originalOffset);
        return null;
    }

    @Override
    protected InvokerResult getSearchResult(Execution execution) throws IOException {
        InvokerResult result = new InvokerResult(query, query.getHits());
        List<LeanHit> merged = Collections.emptyList();
        long nextTimeout = query.getTimeLeft();
        var groupingResultAggregator = new GroupingResultAggregator();
        try {
            while (!invokers.isEmpty() && nextTimeout >= 0) {
                SearchInvoker invoker = availableForProcessing.poll(nextTimeout, TimeUnit.MILLISECONDS);
                if (invoker == null) {
                    log.fine(() -> "Search timed out with " + coverageAggregator.getAskedNodes() + " requests made, " +
                            coverageAggregator.getAnsweredNodes() + " responses received");
                    break;
                } else {
                    InvokerResult toMerge = invoker.getSearchResult(execution);
                    merged = mergeResult(result.getResult(), toMerge, merged, groupingResultAggregator);
                    ejectInvoker(invoker);
                }
                nextTimeout = timeoutHandler.nextTimeoutMS(coverageAggregator.getAnsweredNodes());
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for search results", e);
        }
        groupingResultAggregator.toAggregatedHit().ifPresent(h -> result.getResult().hits().add(h));

        insertNetworkErrors(result.getResult());
        CoverageAggregator adjusted = coverageAggregator.adjustedDegradedCoverage((int)searchCluster.dispatchConfig().redundancy(), timeoutHandler);
        result.getResult().setCoverage(adjusted.createCoverage(timeoutHandler));

        int needed = query.getOffset() + query.getHits();
        for (int index = query.getOffset(); (index < merged.size()) && (index < needed); index++) {
            result.getLeanHits().add(merged.get(index));
        }
        query.setOffset(0);  // Now we are all trimmed down
        return result;
    }

    private void insertNetworkErrors(Result result) {
        // Network errors will be reported as errors only when all nodes fail, otherwise they are just traced
        boolean asErrors = coverageAggregator.hasNoAnswers();

        if (!invokers.isEmpty()) {
            String keys = invokers.stream().map(SearchInvoker::distributionKey).map(dk -> dk.map(i -> i.toString()).orElse("(unspecified)"))
                    .collect(Collectors.joining(", "));

            if (asErrors) {
                result.hits().addError(ErrorMessage
                        .createTimeout("Backend communication timeout on all nodes in group (distribution-keys: " + keys + ")"));
            } else {
                query.trace("Backend communication timeout on nodes with distribution-keys: " + keys, 2);
            }
            coverageAggregator.setTimedOut();
        }
        if (alreadyFailedNodes != null) {
            var message = "Connection failure on nodes with distribution-keys: "
                    + alreadyFailedNodes.stream().map(v -> Integer.toString(v)).collect(Collectors.joining(", "));
            if (asErrors) {
                result.hits().addError(ErrorMessage.createBackendCommunicationError(message));
            } else {
                query.trace(message, 2);
            }
            coverageAggregator.setFailedNodes(alreadyFailedNodes.size());
        }
    }

    private List<LeanHit> mergeResult(Result result, InvokerResult partialResult, List<LeanHit> current,
                                      GroupingResultAggregator groupingResultAggregator) {
        coverageAggregator.add(partialResult.getResult().getCoverage(true));

        result.mergeWith(partialResult.getResult());
        List<Hit> partialNonLean = partialResult.getResult().hits().asUnorderedHits();
        for(Hit hit : partialNonLean) {
            if (hit.isAuxiliary()) {
                if (hit instanceof GroupingListHit) {
                    groupingResultAggregator.mergeWith((GroupingListHit) hit);
                } else {
                    result.hits().add(hit);
                }
            }
        }
        if (current.isEmpty() ) {
            return partialResult.getLeanHits();
        }
        List<LeanHit> partial = partialResult.getLeanHits();
        if (partial.isEmpty()) {
            return current;
        }

        int needed = query.getOffset() + query.getHits();
        List<LeanHit> merged = new ArrayList<>(needed);
        int indexCurrent = 0;
        int indexPartial = 0;
        while (indexCurrent < current.size() && indexPartial < partial.size() && merged.size() < needed) {
            LeanHit incomingHit = partial.get(indexPartial);
            LeanHit currentHit = current.get(indexCurrent);

            int cmpRes = currentHit.compareTo(incomingHit);
            if (cmpRes < 0) {
                merged.add(currentHit);
                indexCurrent++;
            } else if (cmpRes > 0) {
                merged.add(incomingHit);
                indexPartial++;
            } else { // Duplicates
                merged.add(currentHit);
                indexCurrent++;
                indexPartial++;
            }
        }
        appendRemainingIfNeeded(merged, needed, current, indexCurrent);
        appendRemainingIfNeeded(merged, needed, partial, indexPartial);
        return merged;
    }

    private void appendRemainingIfNeeded(List<LeanHit> merged, int needed, List<LeanHit> hits, int index) {
        while ((index < hits.size()) && (merged.size() < needed)) {
            merged.add(hits.get(index++));
        }
    }

    private void ejectInvoker(SearchInvoker invoker) {
        invokers.remove(invoker);
        invoker.release();
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
    protected LinkedBlockingQueue<SearchInvoker> newQueue() {
        return new LinkedBlockingQueue<>();
    }

    // For testing
    Collection<SearchInvoker> invokers() { return invokers; }

}
