// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.xml.BundleMapper;

public class RpcResourcePoolComponent extends Component<RpcResourcePoolComponent, ComponentModel> {

    public RpcResourcePoolComponent() {
        super(toComponentModel());
    }

    private static ComponentModel toComponentModel() {
        String className = com.yahoo.search.dispatch.rpc.RpcResourcePool.class.getName();
        return new ComponentModel(className, className, BundleMapper.searchAndDocprocBundle, null);
    }
}
