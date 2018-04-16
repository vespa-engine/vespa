// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.federation;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.chain.Chain;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.ErrorMessage;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.searchchain.FutureResult;
import com.yahoo.search.searchchain.model.federation.FederationOptions;
import com.yahoo.test.ManualClock;
import org.junit.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bratseth
 */
public class FederationResultTest {
    
    private static final FederationSearcher.Target organic = new MockTarget("organic", 500);
    private static final FederationSearcher.Target dsp1 = new MockTarget("dsp1", 240);
    private static final FederationSearcher.Target dsp2 = new MockTarget("dsp2", 200);

    private final ManualClock clock = new ManualClock();

    @Test
    public void testFederationResult() {
        assertTimeout(ImmutableSet.of(),               100, 200, 180);
        assertTimeout(ImmutableSet.of(),               480, 400, 400);
        assertTimeout(ImmutableSet.of("dsp1"),         260, 280, 220);
        assertTimeout(ImmutableSet.of("organic"),      520, 160, 160);
        assertTimeout(ImmutableSet.of("dsp2"),         200, 220, 230);
        assertTimeout(ImmutableSet.of(),               200, 220, 210);
        assertTimeout(ImmutableSet.of("dsp1", "dsp2"), 200, 260, 260);
        assertTimeout(ImmutableSet.of("organic"),      520, 260, 260);
    }

    private void assertTimeout(Set<String> expectedTimeoutNames, int ... responseTimes) {
        FederationResult.Builder builder = new FederationResult.Builder();
        builder.add(organic, resultAfter(responseTimes[0]));
        builder.add(dsp1,    resultAfter(responseTimes[1]));
        builder.add(dsp2,    resultAfter(responseTimes[2]));
        FederationResult federationResult = builder.build();
        federationResult.waitForAll(50, clock);
        assertEquals(3, federationResult.all().size());
        for (FederationResult.TargetResult targetResult : federationResult.all()) {
            Result result = targetResult.getOrTimeoutError();
            if (expectedTimeoutNames.contains(targetResult.target.getId().toString()))
                assertTrue(targetResult.target.getId() + " timed out", timedOut(result));
            else
                assertTrue(targetResult.target.getId() + " did not time out", ! timedOut(result));
        }
    }
    
    private MockFutureResult resultAfter(int time) {
        return new MockFutureResult(new Query(), time);        
    }
    
    private boolean timedOut(Result result) {
        ErrorMessage error = result.hits().getError();
        if (error == null) return false;
        return error.getCode() == ErrorMessage.timeoutCode;
    }

    private class MockFutureResult extends FutureResult {
        
        private final int responseTime;
        private final Query query;
        private final long startTime;
        
        MockFutureResult(Query query, int responseTime) {
            super(() -> new Result(query), new Execution(Execution.Context.createContextStub()), query);
            this.responseTime = responseTime;
            this.query = query;
            startTime = clock.millis();
        }

        @Override
        public Result get() { throw new RuntimeException(); }

        @Override
        public Optional<Result> getIfAvailable(long timeout, TimeUnit timeunit) {
            if (timeunit != TimeUnit.MILLISECONDS) throw new RuntimeException();

            long elapsedTime = clock.millis() - startTime;
            long leftUntilResponse = responseTime - elapsedTime;
            if (leftUntilResponse > timeout) {
                clock.advance(Duration.ofMillis(timeout));
                return Optional.empty();
            }
            else {
                if (leftUntilResponse > 0) // otherwise we already spent more time than this sources timeout
                    clock.advance(Duration.ofMillis(leftUntilResponse));
                return Optional.of(new Result(query));
            }
        }
        
        @Override
        public Query getQuery() {
            return query;
        }

    }
    
    private static class MockTarget extends FederationSearcher.Target {

        private final Chain<Searcher> chain;
        private final int timeout;
        
        MockTarget(String id, int timeout) {
            this.chain = new Chain<>(id);
            this.timeout = timeout;
        }

        @Override
        Chain<Searcher> getChain() { return chain; }

        @Override
        void modifyTargetQuery(Query query) { }

        @Override
        void modifyTargetResult(Result result) { }

        @Override
        public FederationOptions federationOptions() {
            return new FederationOptions(false, timeout, true);
        }

    }
    
}
