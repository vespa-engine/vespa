// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.component.ComponentId;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import static com.yahoo.component.ComponentSpecification.fromString;

/**
 * @author bjorncs
 */
public abstract class SslProvider extends SimpleComponent {

    public SslProvider(String componentIdPrefix, String serverName, String className, String bundleName) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(new ComponentId(componentIdPrefix+serverName),
                        fromString(className),
                        fromString(bundleName))));
    }

    public abstract void amendConnectorConfig(ConnectorConfig.Builder builder);
}
