// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import java.io.PrintStream;

/**
 * An interface for components supplying a state snapshot where persistence and
 * other pre-processing has been done.
 *
 * @author Steinar Knutsen
 */
public interface SnapshotProvider {

    MetricSnapshot latestSnapshot();
    void histogram(PrintStream output);

}
