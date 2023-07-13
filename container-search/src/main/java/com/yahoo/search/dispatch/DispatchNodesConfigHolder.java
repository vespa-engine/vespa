package com.yahoo.search.dispatch;

import com.yahoo.component.annotation.Inject;
import com.yahoo.vespa.config.search.DispatchNodesConfig;

/**
 * @author jonmv
 */
public class DispatchNodesConfigHolder {

    private final DispatchNodesConfig config;

    @Inject
    public DispatchNodesConfigHolder(DispatchNodesConfig config) {
        this.config = config;
    }

    public DispatchNodesConfig config() {
        return config;
    }

}
