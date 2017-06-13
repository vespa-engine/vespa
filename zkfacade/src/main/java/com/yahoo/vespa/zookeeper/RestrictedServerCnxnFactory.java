// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.common.collect.ImmutableSet;
import com.yahoo.net.HostName;
import com.yahoo.text.StringUtilities;
import org.apache.zookeeper.server.NIOServerCnxn;
import org.apache.zookeeper.server.NIOServerCnxnFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * This class is created by zookeeper by reflection, see the ZooKeeperServer constructor.
 * 
 * @author bratseth
 */
@SuppressWarnings("unused")
public class RestrictedServerCnxnFactory extends NIOServerCnxnFactory {

    private static final Logger log = Logger.getLogger(RestrictedServerCnxnFactory.class.getName());
    
    public RestrictedServerCnxnFactory() throws IOException {
        super();
    }
    
    @Override
    protected NIOServerCnxn createConnection(SocketChannel socket, SelectionKey selection) throws IOException {
        NIOServerCnxn ret = super.createConnection(socket, selection);
        validateRemoteOrClose(socket);
        return ret;
    }

    private void validateRemoteOrClose(SocketChannel socket) {
        try {
            String remoteHost = ((InetSocketAddress)socket.getRemoteAddress()).getHostName();

            if (isLocalHost(remoteHost)) return; // always allow localhost

            ImmutableSet<String> allowedZooKeeperClients = findAllowedZooKeeperClients();

            if (allowedZooKeeperClients.isEmpty()) return; // inactive: allow all
            if (allowedZooKeeperClients.contains(remoteHost)) return; // allowed

            // Not allowed: Reject connection
            String errorMessage = "Rejecting connection to ZooKeeper from " + remoteHost +
                                  ": This cluster only allow connection from hosts in: " + allowedZooKeeperClients;
            log.info(errorMessage);
            socket.shutdownInput();
            socket.shutdownOutput();
        } catch (Exception e) {
            log.warning("Unexpected exception: "+e);
        }
    }

    /** Returns the allowed client host names. If the list is empty any host is allowed. */
    private ImmutableSet<String> findAllowedZooKeeperClients() {
        // Environment has precedence. Note that 
        // - if this is set to "", client restriction is disabled
        // - this environment variable is a public API - do not change
        String environmentAllowedZooKeeperClients = System.getenv("vespa_zkfacade__restrict");
        if (environmentAllowedZooKeeperClients != null) 
            return ImmutableSet.copyOf(toHostnameSet(environmentAllowedZooKeeperClients));

        // No environment setting -> use static field
        return ZooKeeperServer.getAllowedClientHostnames();
    }

    private Set<String> toHostnameSet(String hosatnamesString) {
        Set<String> hostnames = new HashSet<>();
        for (String hostname : StringUtilities.split(hosatnamesString)) {
            if ( ! hostname.trim().isEmpty())
                hostnames.add(hostname.trim());
        }
        return hostnames;
    }

    private boolean isLocalHost(String remoteHost) {
        if (remoteHost.equals("localhost")) return true;
        if (remoteHost.equals("localhost.localdomain")) return true;
        if (remoteHost.equals(HostName.getLocalhost())) return true;
        return false;
    }
    
}
