// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.verification.hardware.benchmarks;

/**
 * @author sgrostad
 * @author olaaun
 */
public interface Benchmark {

    /**
     * Should perform benchmark for some part of the hardware, and store the result in BenchmarkResults instance passed to class
     */
    void doBenchmark();

}
