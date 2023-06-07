// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.cloud.config.DataplaneProxyConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.http.server.jetty.DataplaneProxyCredentials;

/**
 * Reconfigurable component for supporting data plane proxy. Configures the {@code DataplaneProxyService} by calling {@code DataplaneProxyService#init}
 *
 * @author mortent
 */
public class DataplaneProxyConfigurator extends AbstractComponent {

    public DataplaneProxyConfigurator(DataplaneProxyConfig config, DataplaneProxyService dataplaneProxyService, DataplaneProxyCredentials credentialsProvider) {
        dataplaneProxyService.reconfigure(config, credentialsProvider);
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
    }
}
