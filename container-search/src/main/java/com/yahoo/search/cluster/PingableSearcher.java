// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.cluster;

import com.yahoo.component.ComponentId;
import com.yahoo.prelude.Ping;
import com.yahoo.prelude.Pong;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;

/**
 * A searcher to which we can send a ping to probe if it is alive
 *
 * @author bratseth
 */
public abstract class PingableSearcher extends Searcher {

    public PingableSearcher() { }

    public PingableSearcher(ComponentId id) {
        super(id);
    }

    /** Send a ping request downwards to probe if this searcher chain is in functioning order */
    public Pong ping(Ping ping, Execution execution) {
        return execution.ping(ping);
    }

}
