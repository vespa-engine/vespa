// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.cloud.config.DataplaneProxyConfig;
import com.yahoo.container.jdisc.DataplaneProxyConfigurator;
import com.yahoo.vespa.model.container.component.SimpleComponent;

public class DataplaneProxy extends SimpleComponent implements DataplaneProxyConfig.Producer {

    private final int mtlsPort;
    private final int tokenPort;
    private final String serverCertificate;
    private final String serverKey;

    public DataplaneProxy(int mtlsPort, int tokenPort, String serverCertificate, String serverKey) {
        super(DataplaneProxyConfigurator.class.getName());
        this.mtlsPort = mtlsPort;
        this.tokenPort = tokenPort;
        this.serverCertificate = serverCertificate;
        this.serverKey = serverKey;
    }

    @Override
    public void getConfig(DataplaneProxyConfig.Builder builder) {
        builder.mtlsPort(mtlsPort);
        builder.tokenPort(tokenPort);
        builder.serverCertificate(serverCertificate);
        builder.serverKey(serverKey);
    }

}
