// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.vespa.defaults.Defaults;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

public class Configurator {

    private static final java.util.logging.Logger log = java.util.logging.Logger.getLogger(Configurator.class.getName());
    private static final String ZOOKEEPER_JMX_LOG4J_DISABLE = "zookeeper.jmx.log4j.disable";
    static final String ZOOKEEPER_JUTE_MAX_BUFFER = "jute.maxbuffer";

    private final ZookeeperServerConfig zookeeperServerConfig;
    private final Path configFilePath;

    public Configurator(ZookeeperServerConfig zookeeperServerConfig) {
        log.log(Level.FINE, zookeeperServerConfig.toString());
        this.zookeeperServerConfig = zookeeperServerConfig;
        this.configFilePath = makeAbsolutePath(zookeeperServerConfig.zooKeeperConfigFile());
        System.setProperty(ZOOKEEPER_JMX_LOG4J_DISABLE, "true");
        System.setProperty("zookeeper.snapshot.trust.empty", Boolean.valueOf(zookeeperServerConfig.trustEmptySnapshot()).toString());
        System.setProperty(ZOOKEEPER_JUTE_MAX_BUFFER, Integer.valueOf(zookeeperServerConfig.juteMaxBuffer()).toString());
    }

    void writeConfigToDisk(Optional<TlsContext> tlsContext) {
        configFilePath.toFile().getParentFile().mkdirs();

        try {
            writeZooKeeperConfigFile(zookeeperServerConfig, tlsContext);
            writeMyIdFile(zookeeperServerConfig);
        } catch (IOException e) {
            throw new RuntimeException("Error writing zookeeper config", e);
        }
    }

    private void writeZooKeeperConfigFile(ZookeeperServerConfig config,
                                          Optional<TlsContext> tlsContext) throws IOException {
        try (FileWriter writer = new FileWriter(configFilePath.toFile())) {
            writer.write(transformConfigToString(config, tlsContext));
        }
    }

    private String transformConfigToString(ZookeeperServerConfig config, Optional<TlsContext> tlsContext) {
        StringBuilder sb = new StringBuilder();
        sb.append("tickTime=").append(config.tickTime()).append("\n");
        sb.append("initLimit=").append(config.initLimit()).append("\n");
        sb.append("syncLimit=").append(config.syncLimit()).append("\n");
        sb.append("maxClientCnxns=").append(config.maxClientConnections()).append("\n");
        sb.append("snapCount=").append(config.snapshotCount()).append("\n");
        sb.append("dataDir=").append(getDefaults().underVespaHome(config.dataDir())).append("\n");
        sb.append("autopurge.purgeInterval=").append(config.autopurge().purgeInterval()).append("\n");
        sb.append("autopurge.snapRetainCount=").append(config.autopurge().snapRetainCount()).append("\n");
        // See http://zookeeper.apache.org/doc/r3.5.5/zookeeperAdmin.html#sc_zkCommands
        // Includes all available commands in 3.5, except 'wchc' and 'wchp'
        sb.append("4lw.commands.whitelist=conf,cons,crst,dirs,dump,envi,mntr,ruok,srst,srvr,stat,wchs").append("\n");
        sb.append("admin.enableServer=false").append("\n");
        // Need NettyServerCnxnFactory to be able to use TLS for communication
        sb.append("serverCnxnFactory=org.apache.zookeeper.server.NettyServerCnxnFactory").append("\n");
        sb.append("quorumListenOnAllIPs=true").append("\n");
        sb.append("standaloneEnabled=false").append("\n");
        sb.append("reconfigEnabled=true").append("\n");
        sb.append("skipACL=yes").append("\n");
        sb.append("metricsProvider.className=org.apache.zookeeper.metrics.impl.NullMetricsProvider\n");
        ensureThisServerIsRepresented(config.myid(), config.server());
        config.server().forEach(server -> addServerToCfg(sb, server));
        sb.append(new TlsQuorumConfig().createConfig(config, tlsContext));
        sb.append(new TlsClientServerConfig().createConfig(config, tlsContext));
        return sb.toString();
    }

    private void writeMyIdFile(ZookeeperServerConfig config) throws IOException {
        try (FileWriter writer = new FileWriter(getDefaults().underVespaHome(config.myidFile()))) {
            writer.write(config.myid() + "\n");
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
        sb.append("server.")
          .append(server.id())
          .append("=")
          .append(server.hostname())
          .append(":")
          .append(server.quorumPort())
          .append(":")
          .append(server.electionPort());
        if (server.joining()) {
            // Servers that are joining an existing cluster must be marked as observers. Note that this will NOT
            // actually make the server an observer, but prevent it from forming an ensemble independently of the
            // existing cluster.
            //
            // See https://zookeeper.apache.org/doc/r3.6.2/zookeeperReconfig.html#sc_reconfig_modifying
            sb.append(":")
              .append("observer");
        }
        sb.append("\n");
    }

    static List<String> zookeeperServerHostnames(ZookeeperServerConfig zookeeperServerConfig) {
        return zookeeperServerConfig.server().stream()
                                    .map(ZookeeperServerConfig.Server::hostname)
                                    .distinct()
                                    .collect(Collectors.toList());
    }

    Path makeAbsolutePath(String filename) {
        Path path = Paths.get(filename);
        if (path.isAbsolute())
            return path;
        else
            return Paths.get(Defaults.getDefaults().underVespaHome(filename));
    }

    private interface TlsConfig {
        String createConfig(ZookeeperServerConfig config, Optional<TlsContext> tlsContext);

        default Optional<String> getEnvironmentVariable(String variableName) {
            return Optional.ofNullable(System.getenv().get(variableName))
                    .filter(var -> !var.isEmpty());
        }

        default void validateOptions(Optional<TlsContext> tlsContext, String tlsSetting) {
            if (tlsContext.isEmpty() && !tlsSetting.equals("OFF"))
                throw new RuntimeException("Could not retrieve transport security options");
        }

        String configFieldPrefix();

        default void appendTlsConfig(StringBuilder builder, Optional<TlsContext> tlsContext) {
            tlsContext.ifPresent(ctx -> {
                builder.append(configFieldPrefix()).append(".context.supplier.class=").append(VespaSslContextProvider.class.getName()).append("\n");
                String enabledCiphers = Arrays.stream(ctx.parameters().getCipherSuites()).sorted().collect(Collectors.joining(","));
                builder.append(configFieldPrefix()).append(".ciphersuites=").append(enabledCiphers).append("\n");
                String enabledProtocols = Arrays.stream(ctx.parameters().getProtocols()).sorted().collect(Collectors.joining(","));
                builder.append(configFieldPrefix()).append(".enabledProtocols=").append(enabledProtocols).append("\n");
                builder.append(configFieldPrefix()).append(".clientAuth=NEED\n");
            });
        }
    }

    static class TlsClientServerConfig implements TlsConfig {

        @Override
        public String createConfig(ZookeeperServerConfig config, Optional<TlsContext> tlsContext) {
            String tlsSetting = getEnvironmentVariable("VESPA_TLS_FOR_ZOOKEEPER_CLIENT_SERVER_COMMUNICATION")
                    .orElse(config.tlsForClientServerCommunication().name());
            validateOptions(tlsContext, tlsSetting);

            StringBuilder sb = new StringBuilder();
            boolean portUnification;
            boolean secureClientPort;
            switch (tlsSetting) {
                case "OFF":
                    secureClientPort = false; portUnification = false;
                    break;
                case "TLS_ONLY":
                    secureClientPort = true; portUnification = false;
                    break;
                case "PORT_UNIFICATION":
                case "TLS_WITH_PORT_UNIFICATION":
                    secureClientPort = false; portUnification = true;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown value of config setting tlsForClientServerCommunication: " + tlsSetting);
            }
            // ZooKeeper Dynamic Reconfiguration does not support SSL/secure client port
            // The secure client port must be configured in the static configuration section instead
            // https://issues.apache.org/jira/browse/ZOOKEEPER-3577
            sb.append("client.portUnification=").append(portUnification).append("\n")
                    .append("clientPort=").append(secureClientPort ? 0 : config.clientPort()).append("\n")
                    .append("secureClientPort=").append(secureClientPort ? config.clientPort() : 0).append("\n");

            appendTlsConfig(sb, tlsContext);

            return sb.toString();
        }

        @Override
        public String configFieldPrefix() {
            return "ssl";
        }
    }

    static class TlsQuorumConfig implements TlsConfig {

        @Override
        public String createConfig(ZookeeperServerConfig config, Optional<TlsContext> tlsContext) {
            String tlsSetting = getEnvironmentVariable("VESPA_TLS_FOR_ZOOKEEPER_QUORUM_COMMUNICATION")
                    .orElse(config.tlsForQuorumCommunication().name());
            validateOptions(tlsContext, tlsSetting);

            StringBuilder sb = new StringBuilder();
            boolean sslQuorum;
            boolean portUnification;
            switch (tlsSetting) {
                case "OFF":
                    sslQuorum = false;
                    portUnification = false;
                    break;
                case "PORT_UNIFICATION":
                    sslQuorum = false;
                    portUnification = true;
                    break;
                case "TLS_WITH_PORT_UNIFICATION":
                    sslQuorum = true;
                    portUnification = true;
                    break;
                case "TLS_ONLY":
                    sslQuorum = true;
                    portUnification = false;
                    break;
                default: throw new IllegalArgumentException("Unknown value of config setting tlsForQuorumCommunication: " + tlsSetting);
            }
            sb.append("sslQuorum=").append(sslQuorum).append("\n");
            sb.append("portUnification=").append(portUnification).append("\n");
            appendTlsConfig(sb, tlsContext);

            return sb.toString();
        }

        @Override
        public String configFieldPrefix() {
            return "ssl.quorum";
        }
    }

}
