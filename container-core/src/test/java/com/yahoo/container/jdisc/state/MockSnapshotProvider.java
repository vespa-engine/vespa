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
