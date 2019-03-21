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

    public MetricsProxyContainer(AbstractConfigProducer parent, int index) {
        super(parent, "" + index, index);
    }

    @Override
    protected ContainerServiceType myServiceType() {
        return METRICS_PROXY_CONTAINER;
    }

    @Override
    public int getWantedPort() {
        return 19092; // TODO: current metrics-proxy uses 19091 as rpc port, will now get 19093.
    }

    @Override
    public boolean requiresWantedPort() {
        return true;
    }

    @Override
    public int getPortCount() {
        return super.getPortCount() + 1;
    }

    @Override
    protected void tagServers() {
        super.tagServers();
        portsMeta.on(numHttpServerPorts).tag("rpc").tag("metrics");
    }

}
