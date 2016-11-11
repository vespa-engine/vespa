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
import java.util.logging.Level;
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
        ImmutableSet<String> allowedZooKeeperClients = findAllowedZooKeeperClients();
        String remoteHost = ((InetSocketAddress)socket.getRemoteAddress()).getHostName();

        if (isLocalHost(remoteHost)) return super.createConnection(socket, selection); // always allow localhost
        if (allowedZooKeeperClients.isEmpty()) return super.createConnection(socket, selection); // inactive: allow all
        if (allowedZooKeeperClients.contains(remoteHost)) return super.createConnection(socket, selection); // allowed

        // Not allowed: Reject connection
        String errorMessage = "Rejecting connection to ZooKeeper from " + remoteHost +
                              ": This cluster only allow connection from hosts in: " + allowedZooKeeperClients;
        log.info(errorMessage);
        throw new IllegalArgumentException(errorMessage); // log and throw as this exception will be suppressed by zk
    }

    /** Returns the allowed client host names. If the list is empty any host is allowed. */
    private ImmutableSet<String> findAllowedZooKeeperClients() {
        // Environment has precedence. Note that this allows setting restrict to "" to turn off client restriction
        String environmentAllowedZooKeeperClients = System.getenv("vespa_zkfacade__restrict");
        if (environmentAllowedZooKeeperClients != null) 
            return ImmutableSet.copyOf(toHostnameSet(environmentAllowedZooKeeperClients));

        // No environment setting -> use static field
        return ZooKeeperServer.getAllowedClientHostnames();
    }

    private Set<String> toHostnameSet(String commaSeparatedString) {
        Set<String> hostnames = new HashSet<>();
        for (String hostname : commaSeparatedString.split(",")) {
            if ( ! hostname.trim().isEmpty())
                hostnames.add(hostname.trim());
        }
        return hostnames;
    }

    private boolean isLocalHost(String remoteHost) {
        if (remoteHost.equals("localhost")) return true;
        if (remoteHost.equals("localhost.localdomain")) return true;
        return false;
    }
    
}
