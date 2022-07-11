// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.PlatformBundles;

public class RpcResourcePoolComponent extends Component<RpcResourcePoolComponent, ComponentModel> {

    public RpcResourcePoolComponent(String clusterName) {
        super(toComponentModel(clusterName));
    }

    private static ComponentModel toComponentModel(String clusterName) {
        String componentId = "rpcresourcepool." + clusterName;
        return new ComponentModel(componentId, com.yahoo.search.dispatch.rpc.RpcResourcePool.class.getName(), PlatformBundles.SEARCH_AND_DOCPROC_BUNDLE);
    }
}
