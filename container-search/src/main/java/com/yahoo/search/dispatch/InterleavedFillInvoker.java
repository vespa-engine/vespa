// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch;

import com.yahoo.prelude.fastsearch.FastHit;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * InterleavedFillInvoker uses multiple {@link FillInvoker} objects to interface with content
 * nodes in parallel. Operationally it first sends requests with all contained invokers and then
 * collects the results.
 *
 * @author ollivir
 */
public class InterleavedFillInvoker extends FillInvoker {
    private final Map<Integer, FillInvoker> invokers;
    private Map<Integer, Result> expectedFillResults = null;

    public InterleavedFillInvoker(Map<Integer, FillInvoker> invokers) {
        this.invokers = invokers;
    }

    @Override
    protected void sendFillRequest(Result result, String summaryClass) {
        expectedFillResults = new HashMap<>();

        for (Iterator<Hit> it = result.hits().deepIterator(); it.hasNext();) {
            Hit hit = it.next();
            if (hit instanceof FastHit) {
                FastHit fhit = (FastHit) hit;
                Result res = expectedFillResults.computeIfAbsent(fhit.getDistributionKey(), dk -> new Result(result.getQuery()));
                res.hits().add(fhit);
            }
        }
        expectedFillResults.forEach((distKey, partialResult) -> {
            FillInvoker invoker = invokers.get(distKey);
            if (invoker != null) {
                invoker.sendFillRequest(partialResult, summaryClass);
            }
        });
    }

    @Override
    protected void getFillResults(Result result, String summaryClass) {
        if (expectedFillResults == null) {
            return;
        }
        expectedFillResults.forEach((distKey, partialResult) -> {
            FillInvoker invoker = invokers.get(distKey);
            if (invoker != null) {
                invoker.getFillResults(partialResult, summaryClass);
            }
        });
    }

    @Override
    protected void release() {
        if (!invokers.isEmpty()) {
            invokers.values().forEach(FillInvoker::close);
            invokers.clear();
        }
    }
}
