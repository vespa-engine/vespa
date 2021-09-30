// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

import com.google.common.collect.ImmutableList;
import com.yahoo.search.Result;
import com.yahoo.search.searchchain.FutureResult;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The result of a federation to targets which knows how to wait for the result from each target.
 * This thread handles multiple threads producing target results but only a single thread may use an instance of this.
 *
 * @author bratseth
 */
class FederationResult {

    /** All targets of this */
    private final List<TargetResult> targetResults;
    
    /** 
     * The remaining targets to wait for. 
     * Other targets are either complete, or should only be included if they are available when we complete
     */
    private List<TargetResult> targetsToWaitFor;
    
    private FederationResult(ImmutableList<TargetResult> targetResults) {
        this.targetResults = targetResults;

        if (targetResults.stream().anyMatch(TargetResult::isMandatory))
            targetsToWaitFor = targetResults.stream().filter(TargetResult::isMandatory).collect(Collectors.toList());
        else
            targetsToWaitFor = new ArrayList<>(targetResults);
    }

    /**
     * Wait on each target for that targets timeout.
     * In the worst case this is the same as waiting for the max target timeout,
     * in the average case it may be much better because lower timeout sources do not get to
     * drive the timeout above their own timeout value.
     * When this completes, results can be accessed from the TargetResults with no blocking
     * (i.e getOrTimeout) without breaking any contract.
     */
    public void waitForAll(int queryTimeout, Clock clock) {
        long startTime = clock.millis();
        while ( ! targetsToWaitFor.isEmpty()) {
            TargetResult nextToWaitFor = targetWithSmallestTimeout(targetsToWaitFor, queryTimeout);
            long timeLeftOfNextTimeout = nextToWaitFor.timeout(queryTimeout) - ( clock.millis() - startTime );
            nextToWaitFor.getIfAvailable(timeLeftOfNextTimeout);
            targetsToWaitFor.remove(nextToWaitFor);
        }
    }
    
    /** Returns an immutable list of the results of this */
    public List<TargetResult> all() { return targetResults; }

    private TargetResult targetWithSmallestTimeout(List<TargetResult> results, int queryTimeout) {
        TargetResult smallest = null;
        for (TargetResult result : results) {
            if (smallest == null || result.timeout(queryTimeout) < smallest.timeout(queryTimeout))
                smallest = result;
        }
        return smallest;        
    }
    
    static class TargetResult {

        final FederationSearcher.Target target;
        private final FutureResult futureResult;

        /** 
         * Single threaded access to result already returned from futureResult, if any.
         * To avoid unnecessary synchronization with the producer thread.
         */
        private Optional<Result> availableResult = Optional.empty();

        private TargetResult(FederationSearcher.Target target, FutureResult futureResult) {
            this.target = target;
            this.futureResult = futureResult;
        }

        private boolean isMandatory() { return ! target.federationOptions().getOptional(); }

        /**
         * Returns the result of this by blocking until timeout if necessary. 
         * 
         * @return the result if available, or empty otherwise
         */
        public Optional<Result> getIfAvailable(long timeout) {
            if (availableResult.isPresent()) return availableResult;
            availableResult = futureResult.getIfAvailable(timeout, TimeUnit.MILLISECONDS);
            availableResult.ifPresent(result -> target.modifyTargetResult(result));
            return availableResult;
        }
        
        /** Returns a result without blocking; if the result is not available one with a timeout error is produced */
        public Result getOrTimeoutError() {
            // The else part is to offload creation of the timeout error
            return getIfAvailable(0).orElse(futureResult.get(0, TimeUnit.MILLISECONDS));
        }
        
        public boolean successfullyCompleted() {
            return futureResult.isDone() && ! futureResult.isCancelled();
        }

        private int timeout(long queryTimeout) {
            return (int)target.federationOptions().getSearchChainExecutionTimeoutInMilliseconds(queryTimeout);
        }
        
        @Override
        public String toString() {
            return "result for " + target;
        }

    }

    public static class Builder {
        
        private final ImmutableList.Builder<TargetResult> results = new ImmutableList.Builder();
        
        public void add(FederationSearcher.Target target, FutureResult futureResult) {
            results.add(new TargetResult(target, futureResult));
        }
        
        public FederationResult build() {
            return new FederationResult(results.build());
        }
        
    }
    
}
