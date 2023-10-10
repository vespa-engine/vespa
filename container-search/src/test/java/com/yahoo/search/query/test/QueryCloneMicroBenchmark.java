// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.query.test;

import com.yahoo.prelude.query.WeightedSetItem;
import com.yahoo.search.Query;

/**
 * @author bratseth
 */
public class QueryCloneMicroBenchmark {

    public void benchmark() {
        int runs = 10000;

        Query query = createQuery();
        for (int i = 0; i<100000; i++) // yes, this much is needed
            query.clone();
        long startTime = System.currentTimeMillis();
        for (int i = 0; i<runs; i++)
            query.clone();
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("Time per clone: " + (totalTime * 1000 * 1000 / runs) + " nanoseconds" );
    }

    private Query createQuery() {
        Query query = new Query();
        query.getModel().getQueryTree().setRoot(createWeightedSet());
        return query;
    }

    private WeightedSetItem createWeightedSet() {
        WeightedSetItem item = new WeightedSetItem("w");
        for (int i = 0; i<1000; i++)
            item.addToken("item" + i, i);
        return item;
    }

    public static void main(String[] args) {
        new QueryCloneMicroBenchmark().benchmark();
    }

}
