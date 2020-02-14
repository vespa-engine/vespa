// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.xml.BundleMapper;

public class RpcResourcePoolComponent extends Component<RpcResourcePoolComponent, ComponentModel> {

    public RpcResourcePoolComponent(DispatcherComponent dispatcher) {
        super(toComponentModel(dispatcher));
    }

    private static ComponentModel toComponentModel(DispatcherComponent dispatcherComponent) {
        String componentId = "rpcresourcepool." + dispatcherComponent.getComponentId().getName();
        return new ComponentModel(componentId,
                "com.yahoo.search.dispatch.rpc.RpcResourcePool",
                BundleMapper.searchAndDocprocBundle,
                null);
    }
}
