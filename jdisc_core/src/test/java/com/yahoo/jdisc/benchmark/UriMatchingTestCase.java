// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.benchmark;

import com.yahoo.jdisc.application.UriPattern;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 */
public class UriMatchingTestCase {

    private static final int NUM_MATCHES = 100;
    private static long preventOptimization = 0;

    @Test
    void requireThatUriPatternMatchingIsFast() {
        List<String> inputs = Arrays.asList(
                "other://host/",
                "scheme://other/",
                "scheme://host/",
                "scheme://host/foo",
                "scheme://host/foo/bar",
                "scheme://host/foo/bar/baz",
                "scheme://host/other",
                "scheme://host/other/bar",
                "scheme://host/other/bar/baz",
                "scheme://host/foo/other",
                "scheme://host/foo/other/baz",
                "scheme://host/foo/bar/other",
                "scheme://host:69/",
                "scheme://host:69/foo",
                "scheme://host:69/foo/bar",
                "scheme://host:69/foo/bar/baz",
                "scheme://host:96/");
        benchmarkMatch("*://*/*", inputs); // warmup

        runBenchmark("*://*/*", inputs);
        runBenchmark("scheme://*/*", inputs);
        runBenchmark("scheme://host/*", inputs);
        runBenchmark("scheme://host:69/*", inputs);
        runBenchmark("scheme://host:69/foo", inputs);
        runBenchmark("scheme://host:69/foo/bar", inputs);
        runBenchmark("scheme://host:69/foo/bar/baz", inputs);
        runBenchmark("*://host:69/foo/bar/baz", inputs);
        runBenchmark("*://*/foo/*", inputs);
        runBenchmark("*://*/foo/*/baz", inputs);
        runBenchmark("*://*/foo/bar/*", inputs);
        runBenchmark("*://*/foo/bar/baz", inputs);
        runBenchmark("*://*/*/bar", inputs);
        runBenchmark("*://*/*/bar/baz", inputs);
        runBenchmark("*://*/*/*/baz", inputs);

        System.out.println(">>>>> " + preventOptimization);
    }

    private static void runBenchmark(String pattern, List<String> inputs) {
        System.out.format("%-30s %10d\n", pattern, benchmarkMatch(pattern, inputs));
    }

    private static long benchmarkMatch(String pattern, List<String> inputs) {
        UriPattern compiled = new UriPattern(pattern);
        List<URI> uriList = new ArrayList<>(inputs.size());
        for (String input : inputs) {
            uriList.add(URI.create(input));
        }
        long now = System.nanoTime();
        for (int i = 0; i < NUM_MATCHES; ++i) {
            for (URI uri : uriList) {
                UriPattern.Match match = compiled.match(uri);
                preventOptimization += match != null ? match.groupCount() : 1;
            }
        }
        return TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - now);
    }
}
