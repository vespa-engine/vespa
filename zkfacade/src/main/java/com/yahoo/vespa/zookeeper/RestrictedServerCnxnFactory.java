package com.yahoo.vespa.zookeeper;

import com.google.common.collect.ImmutableSet;
import org.apache.zookeeper.server.NIOServerCnxn;
import org.apache.zookeeper.server.NIOServerCnxnFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Optional;
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
        String remoteHost = ((InetSocketAddress)socket.getRemoteAddress()).getHostName();

        Optional<ImmutableSet<String>> allowedZooKeeperClients = ZooKeeperServer.getAllowedClientHostnames();
        if ( ! allowedZooKeeperClients.isPresent()) {
            log.fine("Allowing connection to ZooKeeper from " + remoteHost + ", as allowed zooKeeper clients is not set");
            return super.createConnection(socket, selection); // client checking is not activated
        }

        if ( ! remoteHost.equals("localhost") && ! allowedZooKeeperClients.get().contains(remoteHost)) {
            String errorMessage = "Rejecting connection to ZooKeeper from " + remoteHost +
                                  ": This cluster only allow connection from hosts in: " + allowedZooKeeperClients.get();
            if ("true".equals(System.getenv("vespa_zkfacade__restrict"))) {
                log.info(errorMessage);
                throw new IllegalArgumentException(errorMessage);
            }
            else {
                log.fine("Would reject if activated: " + errorMessage);
            }
        }
        log.fine("Allowing connection to ZooKeeper from " + remoteHost + ", as it is in " + allowedZooKeeperClients.get());
        return super.createConnection(socket, selection);
    }

    private Set<String> toHostnameSet(String commaSeparatedString) {
        Set<String> hostnames = new HashSet<>();
        for (String hostname : commaSeparatedString.split(","))
            hostnames.add(hostname.trim());
        return hostnames;
    }

}
