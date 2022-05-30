// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package org.apache.zookeeper.server;

import com.yahoo.vespa.zookeeper.Configurator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.logging.Logger;

/**
 * Overrides secure setting with value from {@link Configurator}.
 * Workaround for incorrect handling of clientSecurePort in combination with ZooKeeper Dynamic Reconfiguration in 3.6.2
 * See https://issues.apache.org/jira/browse/ZOOKEEPER-3577.
 *
 * Using package {@link org.apache.zookeeper.server} as {@link NettyServerCnxnFactory#NettyServerCnxnFactory()} is package-private.
 *
 * @author bjorncs
 */
public class VespaNettyServerCnxnFactory extends NettyServerCnxnFactory {

    private static final Logger log = Logger.getLogger(VespaNettyServerCnxnFactory.class.getName());

    private final boolean isSecure;

    public VespaNettyServerCnxnFactory() {
        super();
        this.isSecure = Configurator.VespaNettyServerCnxnFactory_isSecure;
        boolean portUnificationEnabled = Boolean.getBoolean(NettyServerCnxnFactory.PORT_UNIFICATION_KEY);
        log.info(String.format("For %h: isSecure=%b, portUnification=%b", this, isSecure, portUnificationEnabled));
    }

    @Override
    public void configure(InetSocketAddress addr, int maxClientCnxns, int backlog, boolean secure) throws IOException {
        log.info(String.format("For %h: configured() invoked with parameter 'secure'=%b, overridden to %b", this, secure, isSecure));
        super.configure(addr, maxClientCnxns, backlog, isSecure);
    }
}
