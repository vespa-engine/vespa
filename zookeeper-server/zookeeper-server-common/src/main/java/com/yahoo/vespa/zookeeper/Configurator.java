// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.security.tls.ConfigFileBasedTlsContext;
import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;
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

    public static volatile boolean VespaNettyServerCnxnFactory_isSecure = false;

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
        System.setProperty("zookeeper.snapshot.trust.empty", String.valueOf(zookeeperServerConfig.trustEmptySnapshot()));
        // Max serialization length. Has effect for both client and server.
        // Doc says that it is max size of data in a zookeeper node, but it goes for everything that
        // needs to be serialized, see https://issues.apache.org/jira/browse/ZOOKEEPER-1162 for details
        System.setProperty(ZOOKEEPER_JUTE_MAX_BUFFER, Integer.valueOf(zookeeperServerConfig.juteMaxBuffer()).toString());
        // Need to set this as a system property instead of config, config does not work
        System.setProperty("zookeeper.authProvider.x509", "com.yahoo.vespa.zookeeper.VespaMtlsAuthenticationProvider");
        // Need to set this as a system property, otherwise it will be parsed for _every_ packet and an exception will be thrown (and handled)
        System.setProperty("zookeeper.globalOutstandingLimit", "1000");
        System.setProperty("zookeeper.snapshot.compression.method", zookeeperServerConfig.snapshotMethod());
        System.setProperty("zookeeper.leader.closeSocketAsync", String.valueOf(zookeeperServerConfig.leaderCloseSocketAsync()));
        System.setProperty("zookeeper.learner.asyncSending", String.valueOf(zookeeperServerConfig.learnerAsyncSending()));
        // Enable creation of TTL Nodes.
        System.setProperty("zookeeper.extendedTypesEnabled", "true");
    }

    void writeConfigToDisk() {
        VespaTlsConfig config;
        String cfgFile = zookeeperServerConfig.vespaTlsConfigFile();
        if (cfgFile.isBlank()) {
            config = VespaTlsConfig.fromSystem();
        } else {
            config = VespaTlsConfig.fromConfig(Paths.get(cfgFile));
        }
        writeConfigToDisk(config);
    }

    // override of Vespa TLS config for unit testing
    void writeConfigToDisk(VespaTlsConfig vespaTlsConfig) {
        configFilePath.toFile().getParentFile().mkdirs();

        try {
            writeZooKeeperConfigFile(zookeeperServerConfig, vespaTlsConfig);
            writeMyIdFile(zookeeperServerConfig);
        } catch (IOException e) {
            throw new RuntimeException("Error writing zookeeper config", e);
        }
    }

    private void writeZooKeeperConfigFile(ZookeeperServerConfig config,
                                          VespaTlsConfig vespaTlsConfig) throws IOException {
        try (FileWriter writer = new FileWriter(configFilePath.toFile())) {
            writer.write(transformConfigToString(config, vespaTlsConfig));
        }
    }

    private String transformConfigToString(ZookeeperServerConfig config, VespaTlsConfig vespaTlsConfig) {
        StringBuilder sb = new StringBuilder();
        sb.append("tickTime=").append(config.tickTime()).append("\n");
        sb.append("initLimit=").append(config.initLimit()).append("\n");
        sb.append("syncLimit=").append(config.syncLimit()).append("\n");
        sb.append("maxClientCnxns=").append(config.maxClientConnections()).append("\n");
        sb.append("snapCount=").append(config.snapshotCount()).append("\n");
        sb.append("dataDir=").append(getDefaults().underVespaHome(config.dataDir())).append("\n");
        sb.append("autopurge.purgeInterval=").append(config.autopurge().purgeInterval()).append("\n");
        sb.append("autopurge.snapRetainCount=").append(config.autopurge().snapRetainCount()).append("\n");
        // See http://zookeeper.apache.org/doc/r3.6.3/zookeeperAdmin.html#sc_zkCommands
        // Includes all available commands in 3.6, except 'wchc' and 'wchp'
        sb.append("4lw.commands.whitelist=conf,cons,crst,dirs,dump,envi,mntr,ruok,srst,srvr,stat,wchs").append("\n");
        sb.append("admin.enableServer=false").append("\n");
        // Use custom connection factory for TLS on client port - see class' Javadoc for rationale
        sb.append("serverCnxnFactory=org.apache.zookeeper.server.VespaNettyServerCnxnFactory").append("\n");
        sb.append("quorumListenOnAllIPs=true").append("\n");
        sb.append("standaloneEnabled=false").append("\n");
        sb.append("reconfigEnabled=true").append("\n");
        sb.append("skipACL=yes").append("\n");
        ensureThisServerIsRepresented(config.myid(), config.server());
        config.server().forEach(server -> sb.append(serverSpec(server, server.joining())).append("\n"));
        sb.append(new TlsQuorumConfig().createConfig(vespaTlsConfig));
        sb.append(new TlsClientServerConfig().createConfig(vespaTlsConfig));
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
            throw new RuntimeException("No id in zookeeper server list that corresponds to my id (" + myid + ")");
        }
    }

    static String serverSpec(ZookeeperServerConfig.Server server, boolean joining) {
        StringBuilder sb = new StringBuilder();
        sb.append("server.")
          .append(server.id())
          .append("=")
          .append(server.hostname())
          .append(":")
          .append(server.quorumPort())
          .append(":")
          .append(server.electionPort());
        if (joining) {
            // Servers that are joining an existing cluster must be marked as observers. Note that this will NOT
            // actually make the server an observer, but prevent it from forming an ensemble independently of the
            // existing cluster.
            //
            // See https://zookeeper.apache.org/doc/r3.6.3/zookeeperReconfig.html#sc_reconfig_modifying
            sb.append(":")
              .append("observer");
        }
        sb.append(";")
          .append(server.clientPort());
        return sb.toString();
    }

    static List<String> zookeeperServerHostnames(ZookeeperServerConfig zookeeperServerConfig) {
        return zookeeperServerConfig.server().stream()
                                    .map(ZookeeperServerConfig.Server::hostname)
                                    .distinct()
                                    .collect(Collectors.toList());
    }

    Path makeAbsolutePath(String filename) {
        Path path = Paths.get(filename);
        return path.isAbsolute() ? path : Paths.get(getDefaults().underVespaHome(filename));
    }

    private interface TlsConfig {
        String configFieldPrefix();

        default void appendSharedTlsConfig(StringBuilder builder, VespaTlsConfig vespaTlsConfig) {
            vespaTlsConfig.context().ifPresent(ctx -> {
                VespaSslContextProvider.set(ctx);
                builder.append(configFieldPrefix()).append(".context.supplier.class=").append(VespaSslContextProvider.class.getName()).append("\n");
                String enabledCiphers = Arrays.stream(ctx.parameters().getCipherSuites()).sorted().collect(Collectors.joining(","));
                builder.append(configFieldPrefix()).append(".ciphersuites=").append(enabledCiphers).append("\n");
                String enabledProtocols = Arrays.stream(ctx.parameters().getProtocols()).sorted().collect(Collectors.joining(","));
                builder.append(configFieldPrefix()).append(".enabledProtocols=").append(enabledProtocols).append("\n");
                builder.append(configFieldPrefix()).append(".clientAuth=NEED\n");
            });
        }

        default boolean enablePortUnification(VespaTlsConfig config) {
            return config.tlsEnabled()
                    && (config.mixedMode() == MixedMode.TLS_CLIENT_MIXED_SERVER || config.mixedMode() == MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER);
        }
    }

    static class TlsClientServerConfig implements TlsConfig {

        public String createConfig(VespaTlsConfig vespaTlsConfig) {
            StringBuilder sb = new StringBuilder()
                    .append("client.portUnification=").append(enablePortUnification(vespaTlsConfig)).append("\n");
            // ZooKeeper Dynamic Reconfiguration requires the "non-secure" client port to exist
            // This is a hack to override the secure parameter through our connection factory wrapper
            // https://issues.apache.org/jira/browse/ZOOKEEPER-3577
            VespaNettyServerCnxnFactory_isSecure = vespaTlsConfig.tlsEnabled() && vespaTlsConfig.mixedMode() == MixedMode.DISABLED;
            appendSharedTlsConfig(sb, vespaTlsConfig);

            return sb.toString();
        }

        @Override
        public String configFieldPrefix() {
            return "ssl";
        }
    }

    static class TlsQuorumConfig implements TlsConfig {

        public String createConfig(VespaTlsConfig vespaTlsConfig) {
            StringBuilder sb = new StringBuilder()
                    .append("sslQuorum=").append(vespaTlsConfig.tlsEnabled()).append("\n")
                    .append("portUnification=").append(enablePortUnification(vespaTlsConfig)).append("\n");
            appendSharedTlsConfig(sb, vespaTlsConfig);
            return sb.toString();
        }

        @Override
        public String configFieldPrefix() {
            return "ssl.quorum";
        }
    }

    static class VespaTlsConfig {
        private final TlsContext context;
        private final MixedMode mixedMode;

        VespaTlsConfig(TlsContext context, MixedMode mixedMode) {
            this.context = context;
            this.mixedMode = mixedMode;
        }

        static VespaTlsConfig fromSystem() {
            return new VespaTlsConfig(
                    TransportSecurityUtils.getSystemTlsContext().orElse(null),
                    TransportSecurityUtils.getInsecureMixedMode());
        }

        static VespaTlsConfig fromConfig(Path file) {
            return new VespaTlsConfig(
                    new ConfigFileBasedTlsContext(file, TransportSecurityUtils.getInsecureAuthorizationMode()),
                    TransportSecurityUtils.getInsecureMixedMode());
        }


        static VespaTlsConfig tlsDisabled() { return new VespaTlsConfig(null, MixedMode.defaultValue()); }

        boolean tlsEnabled() { return context != null; }
        Optional<TlsContext> context() { return Optional.ofNullable(context); }
        MixedMode mixedMode() { return mixedMode; }
    }

}
