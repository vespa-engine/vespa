// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.apps.clustercontroller;

import com.yahoo.component.annotation.Inject;
import com.yahoo.container.jdisc.RequestView;
import com.yahoo.container.jdisc.utils.CapabilityRequiringRequestHandler;
import com.yahoo.security.tls.Capability;
import com.yahoo.vespa.clustercontroller.apputil.communication.http.JDiscHttpRequestHandler;

public class StatusHandler extends JDiscHttpRequestHandler implements CapabilityRequiringRequestHandler {

    private final com.yahoo.vespa.clustercontroller.core.status.StatusHandler statusHandler;

    @Inject
    public StatusHandler(ClusterController fc, JDiscHttpRequestHandler.Context ctx) {
        this(new com.yahoo.vespa.clustercontroller.core.status.StatusHandler(fc), ctx);
    }

    @Override public Capability requiredCapability(RequestView __) { return Capability.CLUSTER_CONTROLLER__STATUS; }

    private StatusHandler(com.yahoo.vespa.clustercontroller.core.status.StatusHandler handler,
                          JDiscHttpRequestHandler.Context ctx)
    {
        super(handler, ctx);
        this.statusHandler = handler;
    }

}
