// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.http;

import com.yahoo.component.ComponentId;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.http.ConnectorConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import org.w3c.dom.Element;

import static com.yahoo.component.ComponentSpecification.fromString;
import static com.yahoo.jdisc.http.ConnectorConfig.Ssl.KeyStoreType;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.21.0
 */
public class ConnectorFactory extends SimpleComponent implements ConnectorConfig.Producer {

    private final String name;
    private volatile int listenPort;
    private final Element legacyConfig;

    public ConnectorFactory(final String name, final int listenPort, final Element legacyConfig) {
        super(new ComponentModel(
                new BundleInstantiationSpecification(new ComponentId(name),
                                                     fromString("com.yahoo.jdisc.http.server.jetty.ConnectorFactory"),
                                                     fromString("jdisc_http_service"))

        ));


        this.name = name;
        this.listenPort = listenPort;
        this.legacyConfig = legacyConfig;
    }

    @Override
    public void getConfig(ConnectorConfig.Builder connectorBuilder) {
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
            if (ssl != null &&
                sslEnabled != null &&
                Boolean.parseBoolean(XML.getValue(sslEnabled).trim())) {
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

        connectorBuilder.listenPort(listenPort);
        connectorBuilder.name(name);
    }

    public String getName() {
        return name;
    }

    public int getListenPort() {
        return listenPort;
    }

    public void setListenPort(int httpPort) {
        this.listenPort = httpPort;
    }

}
