package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.config.server.ConfigServerSpec;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utilities related to file distribution on config servers.
 *
 * @author musum
 * @author gjoranv
 */
public class FileDistributionUtil {


    /**
     * Returns a connection pool with all config servers except this one, or an empty pool if there
     * is only one config server.
     */
    public static ConnectionPool createConnectionPool(ConfigserverConfig configserverConfig) {
        List<String> configServers = ConfigServerSpec.fromConfig(configserverConfig)
                .stream()
                .filter(spec -> !spec.getHostName().equals(HostName.getLocalhost()))
                .map(spec -> "tcp/" + spec.getHostName() + ":" + spec.getConfigServerPort())
                .collect(Collectors.toList());

        return configServers.size() > 0 ? new JRTConnectionPool(new ConfigSourceSet(configServers)) : emptyConnectionPool();
    }

    static ConnectionPool emptyConnectionPool() {
        return new EmptyConnectionPool();
    }

    private static class EmptyConnectionPool implements ConnectionPool {

        @Override
        public void close() {}

        @Override
        public void setError(Connection connection, int i) {}

        @Override
        public Connection getCurrent() { return null; }

        @Override
        public Connection setNewCurrentConnection() { return null; }

        @Override
        public int getSize() { return 0; }

        @Override
        public Supervisor getSupervisor() { return new Supervisor(new Transport()); }
    }

}
