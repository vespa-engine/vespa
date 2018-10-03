// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.component.ComponentId;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import static com.yahoo.component.ComponentSpecification.fromString;

/**
 * @author mortent
 */
public class CustomSslProvider extends SimpleComponent implements ConnectorConfig.Producer {
    public static final String COMPONENT_ID_PREFIX = "ssl-provider@";

    public CustomSslProvider(String serverName, String className, String bundle) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(new ComponentId(COMPONENT_ID_PREFIX + serverName),
                                                     fromString(className),
                                                     fromString(bundle))));
    }

    @Override
    public void getConfig(ConnectorConfig.Builder builder) {
        builder.ssl.enabled(true);
    }
}
