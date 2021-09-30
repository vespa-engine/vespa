// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.jdisc.http.ConnectorConfig;

/**
 * @author mortent
 */
public class CustomSslProvider extends SslProvider {
    public static final String COMPONENT_ID_PREFIX = "ssl-provider@";

    public CustomSslProvider(String serverName, String className, String bundle) {
        super(COMPONENT_ID_PREFIX, serverName, className, bundle);
    }

    @Override
    public void amendConnectorConfig(ConnectorConfig.Builder builder) {
        builder.ssl.enabled(true);
    }
}
