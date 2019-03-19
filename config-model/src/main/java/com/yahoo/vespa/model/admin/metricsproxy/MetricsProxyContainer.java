package com.yahoo.vespa.model.admin.metricsproxy;

import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.Container;

import static com.yahoo.config.model.api.container.ContainerServiceType.METRICS_PROXY_CONTAINER;

/**
 * Container running a metrics proxy.
 *
 * @author gjoranv
 */
public class MetricsProxyContainer extends Container {

    public MetricsProxyContainer(AbstractConfigProducer parent) {
        super(parent, "" + 0, 0);
    }

    @Override
    protected ContainerServiceType myServiceType() {
        return METRICS_PROXY_CONTAINER;
    }

}
