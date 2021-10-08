// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.service.duper;

import com.yahoo.config.provision.NodeType;

public class ProxyHostApplication extends HostAdminApplication {
    public ProxyHostApplication() {
        super("proxy-host", NodeType.proxyhost);
    }
}
