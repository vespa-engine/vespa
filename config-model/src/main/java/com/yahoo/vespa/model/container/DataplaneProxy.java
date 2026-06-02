// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.cloud.config.DataplaneProxyConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;

import java.util.Set;

public class DataplaneProxy extends SimpleComponent implements DataplaneProxyConfig.Producer {

    public static final String COMPONENT_CLASS = "com.yahoo.vespa.cloud.tenant.dataplane.DataplaneProxyConfigurator";

    private final int mtlsPort;
    private final int tokenPort;
    private final String serverCertificate;
    private final String serverKey;
    private final Set<String> tokenEndpoints;

    public DataplaneProxy(int mtlsPort, int tokenPort, String serverCertificate, String serverKey, Set<String> tokenEndpoints) {
        super(new ComponentModel(BundleInstantiationSpecification.fromStrings(COMPONENT_CLASS, null, "cloud-tenant")));
        this.mtlsPort = mtlsPort;
        this.tokenPort = tokenPort;
        this.serverCertificate = serverCertificate;
        this.serverKey = serverKey;
        this.tokenEndpoints = tokenEndpoints;
    }

    @Override
    public void getConfig(DataplaneProxyConfig.Builder builder) {
        builder.mtlsPort(mtlsPort);
        builder.tokenPort(tokenPort);
        builder.serverCertificate(serverCertificate);
        builder.serverKey(serverKey);
        builder.tokenEndpoints(tokenEndpoints);
    }

}
