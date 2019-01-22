// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http.ssl;

import com.yahoo.component.ComponentId;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.ThrowingSslContextFactoryProvider;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import static com.yahoo.component.ComponentSpecification.fromString;

/**
 * @author bjorncs
 */
public class DummySslProvider extends SimpleComponent implements ConnectorConfig.Producer {

    public static final String COMPONENT_ID_PREFIX = "dummy-ssl-provider@";
    public static final String COMPONENT_CLASS = ThrowingSslContextFactoryProvider.class.getName();
    public static final String COMPONENT_BUNDLE = "jdisc_http_service";

    public DummySslProvider(String serverName) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(new ComponentId(COMPONENT_ID_PREFIX + serverName),
                                                     fromString(COMPONENT_CLASS),
                                                     fromString(COMPONENT_BUNDLE))));
    }

    @Override
    public void getConfig(ConnectorConfig.Builder builder) {}
}