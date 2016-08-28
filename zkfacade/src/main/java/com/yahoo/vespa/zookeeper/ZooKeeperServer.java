// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.defaults.Defaults;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Writes zookeeper config and starts zookeeper server.
 *
 * @author lulf
 * @since 5.3
 */
public class ZooKeeperServer extends AbstractComponent implements Runnable {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(ZooKeeperServer.class.getName());
    private static final String ZOOKEEPER_JMX_LOG4J_DISABLE = "zookeeper.jmx.log4j.disable";
    static final String ZOOKEEPER_JUTE_MAX_BUFFER = "jute.maxbuffer";
    static final String ZOOKEEPER_VESPA_SERVERS_PROPERTY = "zookeeper.vespa.servers";
    private final Thread zkServerThread;
    private final ZookeeperServerConfig config;

    ZooKeeperServer(ZookeeperServerConfig config, boolean startServer) {
        this.config = config;
        System.setProperty("zookeeper.jmx.log4j.disable", "true");
        System.setProperty(ZOOKEEPER_JUTE_MAX_BUFFER, "" + config.juteMaxBuffer());

        System.setProperty(ZOOKEEPER_VESPA_SERVERS_PROPERTY, toHostnameString(config.server()));
        System.setProperty("zookeeper.serverCnxnFactory", "com.yahoo.vespa.zookeeper.RestrictedServerCnxnFactory");

        writeConfigToDisk(config);
        zkServerThread = new Thread(this, "zookeeper server");
        if (startServer) {
            zkServerThread.start();
        }
    }

    @Inject
    public ZooKeeperServer(ZookeeperServerConfig config) {
        this(config, true);
    }
    
    private String toHostnameString(List<ZookeeperServerConfig.Server> servers) {
        StringBuilder b = new StringBuilder();
        for (ZookeeperServerConfig.Server server : servers)
            b.append(server.hostname()).append(", ");
        b.setLength(b.length()-1); // remove the last ", "
        return b.toString();
    }

    private void writeConfigToDisk(ZookeeperServerConfig config) {
       String cfg = transformConfigToString(config);
       try (FileWriter writer = new FileWriter(Defaults.getDefaults().underVespaHome(config.zooKeeperConfigFile()))) {
           writer.write(cfg);
           writeMyIdFile(config);
       } catch (IOException e) {
           throw new RuntimeException("Error writing zookeeper config", e);
       }
   }

    private String transformConfigToString(ZookeeperServerConfig config) {
        StringBuilder sb = new StringBuilder();
        sb.append("tickTime=").append(config.tickTime()).append("\n");
        sb.append("initLimit=").append(config.initLimit()).append("\n");
        sb.append("syncLimit=").append(config.syncLimit()).append("\n");
        sb.append("maxClientCnxns=").append(config.maxClientConnections()).append("\n");
        sb.append("snapCount=").append(config.snapshotCount()).append("\n");
        sb.append("dataDir=").append(Defaults.getDefaults().underVespaHome(config.dataDir())).append("\n");
        sb.append("clientPort=").append(config.clientPort()).append("\n");
        sb.append("autopurge.purgeInterval=").append(config.autopurge().purgeInterval()).append("\n");
        sb.append("autopurge.snapRetainCount=").append(config.autopurge().snapRetainCount()).append("\n");
        if (config.server().size() > 1) {
            ensureThisServerIsRepresented(config.myid(), config.server());
            for (ZookeeperServerConfig.Server server : config.server()) {
                addServerToCfg(sb, server);
            }
        }
        return sb.toString();
    }

    private void writeMyIdFile(ZookeeperServerConfig config) throws IOException {
        if (config.server().size() > 1) {
            try (FileWriter writer = new FileWriter(Defaults.getDefaults().underVespaHome(config.myidFile()))) {
                writer.write(config.myid() + "\n");
            }
        }
    }

    private void ensureThisServerIsRepresented(int myid, List<ZookeeperServerConfig.Server> servers) {
        boolean found = false;
        for (ZookeeperServerConfig.Server server : servers) {
            if (myid == server.id()) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new RuntimeException("No id in zookeeper server list that corresponds to my id(" + myid + ")");
        }
    }

    private void addServerToCfg(StringBuilder sb, ZookeeperServerConfig.Server server) {
        sb.append("server.").append(server.id()).append("=").append(server.hostname()).append(":").append(server.quorumPort()).append(":").append(server.electionPort()).append("\n");
    }

    private void shutdown() {
        zkServerThread.interrupt();
        try {
            zkServerThread.join();
        } catch (InterruptedException e) {
            log.log(LogLevel.WARNING, "Error joining server thread on shutdown", e);
        }
    }

    @Override
    public void run() {
        System.setProperty(ZOOKEEPER_JMX_LOG4J_DISABLE, "true");
        String[] args = new String[]{Defaults.getDefaults().underVespaHome(config.zooKeeperConfigFile())};
        log.log(LogLevel.DEBUG, "Starting ZooKeeper server with config: " + args[0]);
        org.apache.zookeeper.server.quorum.QuorumPeerMain.main(args);
    }

    @Override
    public void deconstruct() {
        shutdown();
        super.deconstruct();
    }

    public ZookeeperServerConfig getConfig() { return config; }

}
