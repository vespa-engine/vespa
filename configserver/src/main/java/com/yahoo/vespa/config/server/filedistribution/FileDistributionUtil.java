// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.FileReference;
import com.yahoo.config.subscription.ConfigSourceSet;
import com.yahoo.jrt.Supervisor;
import com.yahoo.jrt.Transport;
import com.yahoo.net.HostName;
import com.yahoo.vespa.config.Connection;
import com.yahoo.vespa.config.ConnectionPool;
import com.yahoo.vespa.config.JRTConnectionPool;
import com.yahoo.vespa.config.server.ConfigServerSpec;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utilities related to file distribution on config servers.
 *
 * @author musum
 * @author gjoranv
 */
public class FileDistributionUtil {

    /**
     * Returns all files in the given directory, non-recursive.
     */
    public static Set<String> getFileReferencesOnDisk(File directory) {
        Set<String> fileReferencesOnDisk = new HashSet<>();
        File[] filesOnDisk = directory.listFiles();
        if (filesOnDisk != null)
            fileReferencesOnDisk.addAll(Arrays.stream(filesOnDisk).map(File::getName).collect(Collectors.toSet()));
        return fileReferencesOnDisk;
    }

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

    public static boolean fileReferenceExistsOnDisk(File downloadDirectory, FileReference applicationPackageReference) {
        return getFileReferencesOnDisk(downloadDirectory).contains(applicationPackageReference.value());
    }

    static ConnectionPool emptyConnectionPool() {
        return new EmptyConnectionPool();
    }

    private static class EmptyConnectionPool implements ConnectionPool {
        private Supervisor supervisor;

        @Override
        public void close() {
            synchronized (this) {
                if (supervisor != null) {
                    supervisor.transport().shutdown().join();
                }
            }
        }

        @Override
        public void setError(Connection connection, int i) {}

        @Override
        public Connection getCurrent() { return null; }

        @Override
        public Connection switchConnection(Connection connection) { return null; }

        @Override
        public int getSize() { return 0; }

        @Override
        public Supervisor getSupervisor() {
            synchronized (this) {
                if (supervisor == null) {
                    supervisor = new Supervisor(new Transport("empty-connectionpool"));
                }
            }
            return supervisor;
        }
    }

}
