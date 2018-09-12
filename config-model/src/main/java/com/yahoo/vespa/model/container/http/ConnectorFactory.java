// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.jdisc.http.ssl.DefaultSslKeyStoreConfigurator;
import com.yahoo.jdisc.http.ssl.DefaultSslTrustStoreConfigurator;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.http.ssl.DummySslProvider;
import org.w3c.dom.Element;

import java.util.Optional;

import static com.yahoo.component.ComponentSpecification.fromString;
import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.KeyStoreType;

/**
 * @author Einar M R Rosenvinge
 * @author bjorncs
 * @author mortent
 */
public class ConnectorFactory extends SimpleComponent implements ConnectorConfig.Producer {

    private final String name;
    private final int listenPort;
    private final Element legacyConfig;

    public ConnectorFactory(String name, int listenPort) {
        this(name, listenPort, null, null, null, new DummySslProvider(name));
    }

    public ConnectorFactory(String name,
                            int listenPort,
                            Element legacyConfig,
                            Element sslKeystoreConfigurator,
                            Element sslTruststoreConfigurator,
                            SimpleComponent sslProviderComponent) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(new ComponentId(name),
                                                     fromString("com.yahoo.jdisc.http.server.jetty.ConnectorFactory"),
                                                     fromString("jdisc_http_service"))));
        this.name = name;
        this.listenPort = listenPort;
        this.legacyConfig = legacyConfig;
        addChild(sslProviderComponent);
        inject(sslProviderComponent);
        addSslKeyStoreConfigurator(name, sslKeystoreConfigurator);
        addSslTrustStoreConfigurator(name, sslTruststoreConfigurator);
    }

    @Override
    public void getConfig(ConnectorConfig.Builder connectorBuilder) {
        configureWithLegacyHttpConfig(legacyConfig, connectorBuilder);
        connectorBuilder.listenPort(listenPort);
        connectorBuilder.name(name);
    }

    public String getName() {
        return name;
    }

    public int getListenPort() {
        return listenPort;
    }

    // TODO Remove support for legacy config in Vespa 7
    @Deprecated
    private static void configureWithLegacyHttpConfig(Element legacyConfig, ConnectorConfig.Builder connectorBuilder) {
        if (legacyConfig != null) {
            {
                Element tcpKeepAliveEnabled = XML.getChild(legacyConfig, "tcpKeepAliveEnabled");
                if (tcpKeepAliveEnabled != null) {
                    connectorBuilder.tcpKeepAliveEnabled(Boolean.valueOf(XML.getValue(tcpKeepAliveEnabled).trim()));
                }
            }
            {
                Element tcpNoDelayEnabled = XML.getChild(legacyConfig, "tcpNoDelayEnabled");
                if (tcpNoDelayEnabled != null) {
                    connectorBuilder.tcpNoDelay(Boolean.valueOf(XML.getValue(tcpNoDelayEnabled).trim()));
                }
            }
            {
                Element tcpListenBacklogLength = XML.getChild(legacyConfig, "tcpListenBacklogLength");
                if (tcpListenBacklogLength != null) {
                    connectorBuilder.acceptQueueSize(Integer.parseInt(XML.getValue(tcpListenBacklogLength).trim()));
                }
            }
            {
                Element idleConnectionTimeout = XML.getChild(legacyConfig, "idleConnectionTimeout");
                if (idleConnectionTimeout != null) {
                    connectorBuilder.idleTimeout(Double.parseDouble(XML.getValue(idleConnectionTimeout).trim()));
                }
            }
            {
                Element soLinger = XML.getChild(legacyConfig, "soLinger");
                if (soLinger != null) {

                    connectorBuilder.soLingerTime(Double.parseDouble(XML.getValue(soLinger).trim()));
                }
            }
            {
                Element sendBufferSize = XML.getChild(legacyConfig, "sendBufferSize");
                if (sendBufferSize != null) {
                    connectorBuilder.outputBufferSize(Integer.parseInt(XML.getValue(sendBufferSize).trim()));
                }
            }
            {
                Element maxHeaderSize = XML.getChild(legacyConfig, "maxHeaderSize");
                if (maxHeaderSize != null) {
                    connectorBuilder.headerCacheSize(Integer.parseInt(XML.getValue(maxHeaderSize).trim()));
                }
            }

            Element ssl = XML.getChild(legacyConfig, "ssl");
            Element sslEnabled = XML.getChild(ssl, "enabled");
            if (ssl != null && sslEnabled != null && Boolean.parseBoolean(XML.getValue(sslEnabled).trim())) {
                ConnectorConfig.Ssl.Builder sslBuilder = new ConnectorConfig.Ssl.Builder();
                sslBuilder.enabled(true);
                {
                    Element keyStoreType = XML.getChild(ssl, "keyStoreType");
                    if (keyStoreType != null) {
                        sslBuilder.keyStoreType(KeyStoreType.Enum.valueOf(XML.getValue(keyStoreType).trim()));
                    }
                }
                {
                    Element keyStorePath = XML.getChild(ssl, "keyStorePath");
                    if (keyStorePath != null) {
                        sslBuilder.keyStorePath(XML.getValue(keyStorePath).trim());
                    }
                }
                {
                    Element trustStorePath = XML.getChild(ssl, "trustStorePath");
                    if (trustStorePath != null) {
                        sslBuilder.trustStorePath(XML.getValue(trustStorePath).trim());
                    }
                }
                {
                    Element keyDBKey = XML.getChild(ssl, "keyDBKey");
                    if (keyDBKey != null) {
                        sslBuilder.keyDbKey(XML.getValue(keyDBKey).trim());
                    }
                }
                {
                    Element algorithm = XML.getChild(ssl, "algorithm");
                    if (algorithm != null) {
                        sslBuilder.sslKeyManagerFactoryAlgorithm(XML.getValue(algorithm).trim());
                    }
                }
                {
                    Element protocol = XML.getChild(ssl, "protocol");
                    if (protocol != null) {
                        sslBuilder.protocol(XML.getValue(protocol).trim());
                    }
                }
                connectorBuilder.ssl(sslBuilder);
            }
        }
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
            configuratorComponent = new SimpleComponent(new ComponentModel(idSpec, defaultImplementation.getName(), "jdisc_http_service"));
        }
        addChild(configuratorComponent);
        inject(configuratorComponent);
    }
}
