package com.yahoo.vespa.zookeeper;

import org.apache.zookeeper.server.NIOServerCnxnFactory;
import org.apache.zookeeper.server.ServerCnxn;

import java.io.IOException;
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
    private final Set<String> zooKeeperServerHostnames;
    
    public RestrictedServerCnxnFactory() throws IOException {
        super();
        zooKeeperServerHostnames = toHostnameSet(System.getProperty(ZooKeeperServer.ZOOKEEPER_VESPA_SERVERS_PROPERTY));
    }
    
    private Set<String> toHostnameSet(String commaSeparatedString) {
        if (commaSeparatedString == null || commaSeparatedString.isEmpty())
            throw new IllegalArgumentException("We have not received the list of ZooKeeper servers in this system");
        
        Set<String> hostnames = new HashSet<>();
        for (String hostname : commaSeparatedString.split(","))
            hostnames.add(hostname.trim());
        return hostnames;
    }

    @Override
    public void registerConnection(ServerCnxn connection) {
        String remoteHost = connection.getRemoteSocketAddress().getHostName();
        if ( ! zooKeeperServerHostnames.contains(remoteHost)) {
            String errorMessage = "Rejecting connection to ZooKeeper from " + remoteHost +
                                  ": This cluster only allow connection from nodes in this cluster. " +
                                  "Hosts in this cluster: " + zooKeeperServerHostnames;
            log.warning(errorMessage);
            throw new IllegalArgumentException(errorMessage);
        }
        super.registerConnection(connection);
    }

}
