// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.query.test;

import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.QueryCanonicalizer;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.query.QueryTree;

/**
 * @author bratseth
 */
public class QueryCanonicalizerMicroBenchmark {

    public void run() {
        System.out.println("Running ...");
        for (int i = 0; i < 10*1000; i++)
            canonicalize();
        long startTime = System.currentTimeMillis();
        int repetitions = 10 * 1000 * 1000;
        for (int i = 0; i < repetitions; i++)
            canonicalize();
        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("Total time: " + totalTime + " ms\nTime per canonicalization: " +
                           1000*1000*totalTime/(float)repetitions + " ns");
    }

    private void canonicalize() {
        AndItem and = new AndItem();
        and.addItem(new WordItem("shoe", "prod"));
        and.addItem(new WordItem("apparel & accessories", "tcnm"));
        RankItem rank = new RankItem();
        rank.addItem(and);
        for (int i = 0; i < 25; i++)
            rank.addItem(new WordItem("word" + i, "normbrnd"));
        QueryTree tree = new QueryTree(rank);
        QueryCanonicalizer.canonicalize(tree);
    }

    public static void main(String[] args) {
        new QueryCanonicalizerMicroBenchmark().run();
    }

}
