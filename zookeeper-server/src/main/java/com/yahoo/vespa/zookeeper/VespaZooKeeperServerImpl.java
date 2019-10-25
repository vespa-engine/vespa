// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.google.inject.Inject;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.AbstractComponent;
import com.yahoo.log.LogLevel;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes zookeeper config and starts zookeeper server.
 *
 * @author Ulf Lilleengen
 * @author Harald Musum
 */
public class VespaZooKeeperServerImpl extends AbstractComponent implements Runnable, VespaZooKeeperServer {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(VespaZooKeeperServerImpl.class.getName());
    private static final String ZOOKEEPER_JMX_LOG4J_DISABLE = "zookeeper.jmx.log4j.disable";
    static final String ZOOKEEPER_JUTE_MAX_BUFFER = "jute.maxbuffer";
    private final Thread zkServerThread;
    private final ZookeeperServerConfig zookeeperServerConfig;

    VespaZooKeeperServerImpl(ZookeeperServerConfig zookeeperServerConfig, boolean startServer) {
        this.zookeeperServerConfig = zookeeperServerConfig;
        System.setProperty("zookeeper.jmx.log4j.disable", "true");
        System.setProperty("zookeeper.snapshot.trust.empty", Boolean.valueOf(zookeeperServerConfig.trustEmptySnapshot()).toString());
        System.setProperty(ZOOKEEPER_JUTE_MAX_BUFFER, Integer.valueOf(zookeeperServerConfig.juteMaxBuffer()).toString());

        writeConfigToDisk(zookeeperServerConfig);
        zkServerThread = new Thread(this, "zookeeper server");
        if (startServer) {
            zkServerThread.start();
        }
    }

    @Inject
    public VespaZooKeeperServerImpl(ZookeeperServerConfig zookeeperServerConfig) {
        this(zookeeperServerConfig, true);
    }

    private void writeConfigToDisk(ZookeeperServerConfig config) {
       String configFilePath = getDefaults().underVespaHome(config.zooKeeperConfigFile());
       new File(configFilePath).getParentFile().mkdirs();
       try (FileWriter writer = new FileWriter(configFilePath)) {
           writer.write(transformConfigToString(config));
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
        sb.append("dataDir=").append(getDefaults().underVespaHome(config.dataDir())).append("\n");
        sb.append("clientPort=").append(config.clientPort()).append("\n");
        sb.append("autopurge.purgeInterval=").append(config.autopurge().purgeInterval()).append("\n");
        sb.append("autopurge.snapRetainCount=").append(config.autopurge().snapRetainCount()).append("\n");
        // See http://zookeeper.apache.org/doc/r3.4.13/zookeeperAdmin.html#sc_zkCommands
        // Includes all available commands in 3.4, except 'wchc' and 'wchp'
        // Mandatory when using ZooKeeper 3.5
        sb.append("4lw.commands.whitelist=conf,cons,crst,dump,envi,mntr,ruok,srst,srvr,stat,wchs").append("\n");
        ensureThisServerIsRepresented(config.myid(), config.server());
        config.server().forEach(server -> addServerToCfg(sb, server));
        return sb.toString();
    }

    private void writeMyIdFile(ZookeeperServerConfig config) throws IOException {
        if (config.server().size() > 1) {
            try (FileWriter writer = new FileWriter(getDefaults().underVespaHome(config.myidFile()))) {
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
        String[] args = new String[]{getDefaults().underVespaHome(zookeeperServerConfig.zooKeeperConfigFile())};
        log.log(LogLevel.DEBUG, "Starting ZooKeeper server with config file " + args[0]);
        log.log(LogLevel.INFO, "Trying to establish ZooKeeper quorum (from " + zookeeperServerHostnames(zookeeperServerConfig) + ")");
        org.apache.zookeeper.server.quorum.QuorumPeerMain.main(args);
    }

    @Override
    public void deconstruct() {
        shutdown();
        super.deconstruct();
    }

    private static Set<String> zookeeperServerHostnames(ZookeeperServerConfig zookeeperServerConfig) {
        return zookeeperServerConfig.server().stream().map(ZookeeperServerConfig.Server::hostname).collect(Collectors.toSet());
    }

}
