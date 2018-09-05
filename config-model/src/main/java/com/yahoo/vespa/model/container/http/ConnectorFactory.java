// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.DefaultSslKeyStoreConfigurator;
import com.yahoo.jdisc.http.ssl.DefaultSslTrustStoreConfigurator;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import org.w3c.dom.Element;

import static com.yahoo.component.ComponentSpecification.fromString;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 */
public class ConnectorFactory extends SimpleComponent implements ConnectorConfig.Producer {

    private final String name;
    private final int listenPort;

    public ConnectorFactory(String name, int listenPort) {
        this(name, listenPort, null, null);
    }

    public ConnectorFactory(String name,
                            int listenPort,
                            Element sslKeystoreConfigurator,
                            Element sslTruststoreConfigurator) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(new ComponentId(name),
                                                     fromString("com.yahoo.jdisc.http.server.jetty.ConnectorFactory"),
                                                     fromString("jdisc_http_service"))));
        this.name = name;
        this.listenPort = listenPort;
        addSslKeyStoreConfigurator(name, sslKeystoreConfigurator);
        addSslTrustStoreConfigurator(name, sslTruststoreConfigurator);
    }

    @Override
    public void getConfig(ConnectorConfig.Builder connectorBuilder) {
        connectorBuilder.listenPort(listenPort);
        connectorBuilder.name(name);
    }

    public String getName() {
        return name;
    }

    public int getListenPort() {
        return listenPort;
    }

    private void addSslKeyStoreConfigurator(String name, Element sslKeystoreConfigurator) {
        addSslConfigurator("ssl-keystore-configurator@" + name,
                           DefaultSslKeyStoreConfigurator.class,
                           sslKeystoreConfigurator);
    }

    private void addSslTrustStoreConfigurator(String name, Element sslKeystoreConfigurator) {
        addSslConfigurator("ssl-truststore-configurator@" + name,
                           DefaultSslTrustStoreConfigurator.class,
                           sslKeystoreConfigurator);
    }

    private void addSslConfigurator(String idSpec, Class<?> defaultImplementation, Element configuratorElement) {
        SimpleComponent configuratorComponent;
        if (configuratorElement != null) {
            String className = configuratorElement.getAttribute("class");
            String bundleName = configuratorElement.getAttribute("bundle");
            configuratorComponent = new SimpleComponent(new ComponentModel(idSpec, className, bundleName));
        } else {
            configuratorComponent =
                    new SimpleComponent(new ComponentModel(idSpec, defaultImplementation.getName(), "jdisc_http_service"));
        }
        addChild(configuratorComponent);
        inject(configuratorComponent);
    }

}
