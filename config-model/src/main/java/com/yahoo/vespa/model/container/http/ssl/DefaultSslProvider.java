// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.impl.DefaultSslContextFactoryProvider;

/**
 * @author bjorncs
 */
public class DefaultSslProvider extends SslProvider {

    public static final String COMPONENT_ID_PREFIX = "default-ssl-provider@";
    public static final String COMPONENT_CLASS = DefaultSslContextFactoryProvider.class.getName();

    public DefaultSslProvider(String serverName) {
        super(COMPONENT_ID_PREFIX, serverName, COMPONENT_CLASS, null);
    }

    @Override public void amendConnectorConfig(ConnectorConfig.Builder builder) {}
}
