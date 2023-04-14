// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig.Server;
import com.yahoo.security.tls.ConfigFileBasedTlsContext;
import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.security.tls.TransportSecurityUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static com.yahoo.stream.CustomCollectors.toLinkedMap;
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
        String dynamicConfigPath = config.dynamicReconfiguration() ? parseConfigFile(configFilePath).get("dynamicConfigFile") : null;
        Map<String, String> dynamicConfig = dynamicConfigPath != null ? parseConfigFile(Paths.get(dynamicConfigPath)) : Map.of();
        try (FileWriter writer = new FileWriter(configFilePath.toFile())) {
            writer.write(transformConfigToString(config, vespaTlsConfig, dynamicConfig));
        }
    }

    private String transformConfigToString(ZookeeperServerConfig config, VespaTlsConfig vespaTlsConfig, Map<String, String> dynamicConfig) {
        Map<String, String> configEntries = new LinkedHashMap<>();
        configEntries.put("tickTime", Integer.toString(config.tickTime()));
        configEntries.put("initLimit", Integer.toString(config.initLimit()));
        configEntries.put("syncLimit", Integer.toString(config.syncLimit()));
        configEntries.put("maxClientCnxns", Integer.toString(config.maxClientConnections()));
        configEntries.put("snapCount", Integer.toString(config.snapshotCount()));
        configEntries.put("dataDir", getDefaults().underVespaHome(config.dataDir()));
        configEntries.put("autopurge.purgeInterval", Integer.toString(config.autopurge().purgeInterval()));
        configEntries.put("autopurge.snapRetainCount", Integer.toString(config.autopurge().snapRetainCount()));
        // See http://zookeeper.apache.org/doc/r3.6.3/zookeeperAdmin.html#sc_zkCommands
        // Includes all available commands in 3.6, except 'wchc' and 'wchp'
        configEntries.put("4lw.commands.whitelist", "conf,cons,crst,dirs,dump,envi,mntr,ruok,srst,srvr,stat,wchs");
        configEntries.put("admin.enableServer", "false");
        // Use custom connection factory for TLS on client port - see class' Javadoc for rationale
        configEntries.put("serverCnxnFactory", "org.apache.zookeeper.server.VespaNettyServerCnxnFactory");
        configEntries.put("quorumListenOnAllIPs", "true");
        configEntries.put("standaloneEnabled", "false");
        configEntries.put("reconfigEnabled", Boolean.toString(config.dynamicReconfiguration()));
        configEntries.put("skipACL", "yes");

        addServerSpecs(configEntries, config, dynamicConfig);

        new TlsQuorumConfig().createConfig(configEntries, vespaTlsConfig);
        new TlsClientServerConfig().createConfig(configEntries, vespaTlsConfig);
        return transformConfigToString(configEntries);
    }

    void addServerSpecs(Map<String, String> configEntries, ZookeeperServerConfig config, Map<String, String> dynamicConfig) {
        int myIndex = ensureThisServerIsRepresented(config.myid(), config.server());

        // If dynamic config refers to servers that are not in the current config, we must ignore it.
        Set<String> currentServers = config.server().stream().map(Server::hostname).collect(Collectors.toSet());
        if (dynamicConfig.values().stream().anyMatch(spec -> ! currentServers.contains(spec.split(":", 2)[0]))) {
            log.log(Level.WARNING, "Existing dynamic config refers to unknown servers, ignoring it");
            dynamicConfig = Map.of();
        }

        // If we have no existing, valid, dynamic config, we use all known servers as a starting point.
        if (dynamicConfig.isEmpty()) {
            configEntries.putAll(getServerConfig(config.server(), config.server(myIndex).joining() ? config.myid() : -1));
        }
        // Otherwise, we use the existing, dynamic config as a starting point, and add this as a joiner if not present.
        else {
            Map.Entry<String, String> thisAsAJoiner = getServerConfig(config.server().subList(myIndex, myIndex + 1), config.myid()).entrySet().iterator().next();
            dynamicConfig.putIfAbsent(thisAsAJoiner.getKey(), thisAsAJoiner.getValue());
            configEntries.putAll(dynamicConfig);
        }

    }
    static Map<String, String> getServerConfig(List<ZookeeperServerConfig.Server> serversConfig, int joinerId) {
        Map<String, String> configEntries = new LinkedHashMap<>();
        for (Server server : serversConfig) {
            configEntries.put("server." + server.id(), serverSpec(server, server.id() == joinerId));
        }
        return configEntries;
    }

    static String transformConfigToString(Map<String, String> config) {
        return config.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private void writeMyIdFile(ZookeeperServerConfig config) throws IOException {
        try (FileWriter writer = new FileWriter(getDefaults().underVespaHome(config.myidFile()))) {
            writer.write(config.myid() + "\n");
        }
    }

    private static int ensureThisServerIsRepresented(int myid, List<ZookeeperServerConfig.Server> servers) {
        for (int i = 0; i < servers.size(); i++) {
            Server server = servers.get(i);
            if (myid == server.id()) return i;
        }
        throw new RuntimeException("No id in zookeeper server list that corresponds to my id (" + myid + ")");
    }

    static String serverSpec(ZookeeperServerConfig.Server server, boolean joining) {
        StringBuilder sb = new StringBuilder();
        sb.append(server.hostname())
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

    static Map<String, String> parseConfigFile(Path configFilePath) {
        try {
            return Files.exists(configFilePath) ? Files.readAllLines(configFilePath).stream()
                                                       .filter(line -> ! line.startsWith("#"))
                                                       .map(line -> line.split("=", 2))
                                                       .collect(toLinkedMap(parts -> parts[0], parts -> parts[1]))
                                                : Map.of();
        }
        catch (IOException e) {
            throw new UncheckedIOException("error reading zookeeper config", e);
        }
    }

    static List<String> zookeeperServerHostnames(ZookeeperServerConfig zookeeperServerConfig) {
        return zookeeperServerConfig.server().stream()
                                    .map(ZookeeperServerConfig.Server::hostname)
                                    .distinct()
                                    .toList();
    }

    Path makeAbsolutePath(String filename) {
        Path path = Paths.get(filename);
        return path.isAbsolute() ? path : Paths.get(getDefaults().underVespaHome(filename));
    }

    private interface TlsConfig {
        String configFieldPrefix();

        default void appendSharedTlsConfig(Map<String, String> configEntries, VespaTlsConfig vespaTlsConfig) {
            vespaTlsConfig.context().ifPresent(ctx -> {
                VespaSslContextProvider.set(ctx);
                configEntries.put(configFieldPrefix() + ".context.supplier.class", VespaSslContextProvider.class.getName());
                String enabledCiphers = Arrays.stream(ctx.parameters().getCipherSuites()).sorted().collect(Collectors.joining(","));
                configEntries.put(configFieldPrefix() + ".ciphersuites", enabledCiphers);
                String enabledProtocols = Arrays.stream(ctx.parameters().getProtocols()).sorted().collect(Collectors.joining(","));
                configEntries.put(configFieldPrefix() + ".enabledProtocols", enabledProtocols);
                configEntries.put(configFieldPrefix() + ".clientAuth", "NEED");
            });
        }

        default boolean enablePortUnification(VespaTlsConfig config) {
            return config.tlsEnabled()
                    && (config.mixedMode() == MixedMode.TLS_CLIENT_MIXED_SERVER || config.mixedMode() == MixedMode.PLAINTEXT_CLIENT_MIXED_SERVER);
        }
    }

    static class TlsClientServerConfig implements TlsConfig {

        public void createConfig(Map<String, String> configEntries, VespaTlsConfig vespaTlsConfig) {
            configEntries.put("client.portUnification", String.valueOf(enablePortUnification(vespaTlsConfig)));
            // ZooKeeper Dynamic Reconfiguration requires the "non-secure" client port to exist
            // This is a hack to override the secure parameter through our connection factory wrapper
            // https://issues.apache.org/jira/browse/ZOOKEEPER-3577
            VespaNettyServerCnxnFactory_isSecure = vespaTlsConfig.tlsEnabled() && vespaTlsConfig.mixedMode() == MixedMode.DISABLED;
            appendSharedTlsConfig(configEntries, vespaTlsConfig);
        }

        @Override
        public String configFieldPrefix() {
            return "ssl";
        }
    }

    static class TlsQuorumConfig implements TlsConfig {

        public void createConfig(Map<String, String> configEntries, VespaTlsConfig vespaTlsConfig) {
            configEntries.put("sslQuorum", String.valueOf(vespaTlsConfig.tlsEnabled()));
            configEntries.put("portUnification", String.valueOf(enablePortUnification(vespaTlsConfig)));
            appendSharedTlsConfig(configEntries, vespaTlsConfig);
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
