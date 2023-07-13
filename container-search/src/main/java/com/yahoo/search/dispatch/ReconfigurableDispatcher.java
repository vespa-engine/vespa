package com.yahoo.search.dispatch;

import com.yahoo.component.ComponentId;
import com.yahoo.component.annotation.Inject;
import com.yahoo.container.handler.VipStatus;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;

public class ReconfigurableDispatcher extends Dispatcher {

    @Inject
    public ReconfigurableDispatcher(ComponentId clusterId, DispatchConfig dispatchConfig, VipStatus vipStatus) {
        super(clusterId, dispatchConfig, new DispatchNodesConfig.Builder().build(), vipStatus);
    }

}
