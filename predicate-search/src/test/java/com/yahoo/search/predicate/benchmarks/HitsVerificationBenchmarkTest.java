// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate.benchmarks;

import com.google.common.primitives.Ints;
import com.yahoo.search.predicate.Config;
import com.yahoo.search.predicate.PredicateIndex;
import com.yahoo.search.predicate.index.BoundsPostingList;
import com.yahoo.search.predicate.index.IntervalWithBounds;
import com.yahoo.search.predicate.index.PredicateIntervalStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HitsVerificationBenchmarkTest {

    @Test
    void testTargeting() throws IOException {
        HitsVerificationBenchmark.BenchmarkArguments args = new HitsVerificationBenchmark.BenchmarkArguments();
        args.feedFile = "src/test/resources/targeting.json";
        Config config = new Config.Builder().build();
        Map<String, Object> output = new HashMap<>();
        HitsVerificationBenchmark.getIndex(args, config, output);
        assertEquals(46, output.get("Interval index entries"));
    }

    @Test
    void testFeed() throws IOException {
        HitsVerificationBenchmark.BenchmarkArguments args = new HitsVerificationBenchmark.BenchmarkArguments();
        args.feedFile = "src/test/resources/vespa-feed.json";
        Config config = new Config.Builder().build();
        Map<String, Object> output = new HashMap<>();
        HitsVerificationBenchmark.getIndex(args, config, output);
        assertEquals(206, output.get("Interval index entries"));
    }

}
