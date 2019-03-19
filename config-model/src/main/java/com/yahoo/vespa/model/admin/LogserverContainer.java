// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.vespa.model.container.component.Handler;

/**
 * Container that should be running on same host as the logserver. Sets up a handler for getting logs from logserver.
 * Only in use in hosted Vespa.
 */
public class LogserverContainer extends Container {

    private final boolean useSeparateServiceTypeForLogserverContainer;

    public LogserverContainer(AbstractConfigProducer parent, boolean useSeparateServiceTypeForLogserverContainer) {
        super(parent, "" + 0, 0);
        this.useSeparateServiceTypeForLogserverContainer = useSeparateServiceTypeForLogserverContainer;
    }

    @Override
    public ContainerServiceType myServiceType() {
        return useSeparateServiceTypeForLogserverContainer ? ContainerServiceType.LOGSERVER_CONTAINER : ContainerServiceType.CONTAINER;
    }


}
