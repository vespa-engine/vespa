// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import java.io.PrintStream;

/**
 * @author gjoranv
 */
public class MockSnapshotProvider implements SnapshotProvider {

    private MetricSnapshot snapshot;

    public void setSnapshot(MetricSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    @Override
    public MetricSnapshot latestSnapshot() {
        return snapshot;
    }

    @Override
    public void histogram(PrintStream output) { }
}
