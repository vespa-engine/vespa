// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

import java.util.concurrent.Executor;

/**
 * Must be implemented by a node collection which wants
 * it's node state monitored by a ClusterMonitor
 *
 * @author  <a href="mailto:bratseth@yahoo-inc.com">Jon S Bratseth</a>
 */
public interface NodeManager<T> {

    /** Called when a failed node is working (ready for production) again */
    public void working(T node);

    /** Called when a working node fails */
    public void failed(T node);

    /** Called when a node should be pinged */
    public void ping(T node, Executor executor);

}
