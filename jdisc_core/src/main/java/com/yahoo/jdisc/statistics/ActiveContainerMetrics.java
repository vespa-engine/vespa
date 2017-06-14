// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.statistics;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.core.ActiveContainer;

/**
 * Tracks statistics on stale {@link ActiveContainer} instances.
 *
 * @author bjorncs
 */
public interface ActiveContainerMetrics {
    String TOTAL_DEACTIVATED_CONTAINERS = "jdisc.deactivated_containers.total";
    String DEACTIVATED_CONTAINERS_WITH_RETAINED_REFERENCES = "jdisc.deactivated_containers.with_retained_refs";

    void emitMetrics(Metric metric);
}
