package com.yahoo.search.federation;

import com.google.common.collect.ImmutableList;
import com.yahoo.search.searchchain.FutureResult;
import com.yahoo.search.searchchain.model.federation.FederationOptions;

import java.util.Collection;
import java.util.List;

/**
 * The result of a federation to targets which knowd how to wait for the result from each target.
 *
 * @author bratseth
 */
class FederationResult {

    private final List<TargetResult> targetResults;
    
    private FederationResult(ImmutableList<TargetResult> targetResults) {
        this.targetResults = targetResults;
    }
    
    public void waitForAll(long queryTimeout) {
        waitForMandatoryTargets(queryTimeout); // For now ...
    }
    
    /** Returns an immutable list of the results of this */
    public List<TargetResult> all() { return targetResults; }
    
    private void waitForMandatoryTargets(long queryTimeout) {
        FutureWaiter futureWaiter = new FutureWaiter();

        boolean hasMandatoryTargets = false;
        for (TargetResult targetResult : targetResults) {
            if (targetResult.isMandatory()) {
                futureWaiter.add(targetResult.futureResult, targetResult.getSearchChainExecutionTimeoutMs(queryTimeout));
                hasMandatoryTargets = true;
            }
        }

        if ( ! hasMandatoryTargets) {
            for (TargetResult targetResult : targetResults) {
                futureWaiter.add(targetResult.futureResult,
                                 targetResult.getSearchChainExecutionTimeoutMs(queryTimeout));
            }
        }

        futureWaiter.waitForFutures();
    }

    static class TargetResult {

        final FederationSearcher.Target target;
        final FutureResult futureResult;

        private TargetResult(FederationSearcher.Target target, FutureResult futureResult) {
            this.target = target;
            this.futureResult = futureResult;
        }

        public boolean successfullyCompleted() {
            return futureResult.isDone() && ! futureResult.isCancelled();
        }

        private boolean isMandatory() { return ! target.federationOptions().getOptional(); }

        private long getSearchChainExecutionTimeoutMs(long queryTimeout) {
            return target.federationOptions().getSearchChainExecutionTimeoutInMilliseconds(queryTimeout);
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
    
    /** Returns the max mandatory timeout, or 0 if there are no mandatory sources */
    /*
    private long calculateMandatoryTimeout(Query query, Collection<FederationSearcher.Target> targets) {
        long mandatoryTimeout = 0;
        long queryTimeout = query.getTimeout();
        for (FederationSearcher.Target target : targets) {
            if (target.federationOptions().getOptional()) continue;
            mandatoryTimeout = Math.min(mandatoryTimeout,
                                        target.federationOptions().getSearchChainExecutionTimeoutInMilliseconds(queryTimeout));
        }
        return mandatoryTimeout;
    }

        if (query.requestHasProperty("timeout")) {
        mandatoryTimeout = query.getTimeLeft();
    } else {
        mandatoryTimeout = calculateMandatoryTimeout(query, targetHandlers);
    }

        if (mandatoryTimeout < 0)
            return new Result(query, ErrorMessage.createTimeout("Timed out when about to federate"));
    
    */
}
