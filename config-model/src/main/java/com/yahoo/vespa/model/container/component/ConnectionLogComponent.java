// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.model.container.component;

import com.yahoo.container.logging.ConnectionLog;
import com.yahoo.container.logging.ConnectionLogConfig;
import com.yahoo.osgi.provider.model.ComponentModel;

public class ConnectionLogComponent extends SimpleComponent implements ConnectionLogConfig.Producer {

    private final String clusterName;

    public ConnectionLogComponent(Class<? extends ConnectionLog> cls, String clusterName) {
        super(new ComponentModel(cls.getName(), null, "jdisc_http_service", null));
        this.clusterName = clusterName;
    }

    @Override
    public void getConfig(ConnectionLogConfig.Builder builder) {
        builder.cluster(clusterName);
    }
}
