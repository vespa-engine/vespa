// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.tls.AuthorizationMode;
import com.yahoo.security.tls.AuthorizedPeers;
import com.yahoo.security.tls.DefaultTlsContext;
import com.yahoo.security.tls.HostnameVerification;
import com.yahoo.security.tls.MixedMode;
import com.yahoo.security.tls.PeerAuthentication;
import com.yahoo.security.tls.TlsContext;
import com.yahoo.yolean.Exceptions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import static com.yahoo.security.KeyAlgorithm.EC;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static com.yahoo.vespa.zookeeper.Configurator.VespaTlsConfig;
import static com.yahoo.vespa.zookeeper.Configurator.ZOOKEEPER_JUTE_MAX_BUFFER;
import static java.time.Instant.EPOCH;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.junit.Assert.assertEquals;

/**
 * Tests the zookeeper server.
 */
public class ConfiguratorTest {

    private File cfgFile;
    private File idFile;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        cfgFile = folder.newFile();
        idFile = folder.newFile();
    }

    @Test
    public void config_is_written_correctly_when_one_server() {
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile);
        new Configurator(builder.build()).writeConfigToDisk(VespaTlsConfig.tlsDisabled());
        validateConfigFileSingleHost(cfgFile);
        validateIdFile(idFile, "0\n");
    }

    @Test
    public void config_is_written_correctly_when_multiple_servers() {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.server(newServer(0, "foo", 123, 321, false));
        builder.server(newServer(1, "bar", 234, 432, false));
        builder.server(newServer(2, "baz", 345, 543, true));
        builder.myidFile(idFile.getAbsolutePath());
        builder.myid(1);
        builder.tickTime(1234);
        new Configurator(builder.build()).writeConfigToDisk(VespaTlsConfig.tlsDisabled());
        validateConfigFileMultipleHosts(cfgFile);
        validateIdFile(idFile, "1\n");
    }

    @Test
    public void config_is_written_correctly_with_tls_for_quorum_communication_tls_with_mixed_mode() {
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile);
        TlsContext tlsContext = createTlsContext();
        new Configurator(builder.build()).writeConfigToDisk(new VespaTlsConfig(tlsContext, MixedMode.TLS_CLIENT_MIXED_SERVER));
        validateConfigFileTlsWithMixedMode(cfgFile);
    }

    @Test
    public void config_is_written_correctly_with_tls_for_quorum_communication_tls_without_mixed_mode() {
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile);
        TlsContext tlsContext = createTlsContext();
        new Configurator(builder.build()).writeConfigToDisk(new VespaTlsConfig(tlsContext, MixedMode.DISABLED));
        validateConfigFileTlsWithoutMixedMode(cfgFile);
    }

    @Test(expected = RuntimeException.class)
    public void require_that_this_id_must_be_present_amongst_servers() {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.server(newServer(1, "bar", 234, 432, false));
        builder.server(newServer(2, "baz", 345, 543, false));
        builder.myid(0);
        new Configurator(builder.build()).writeConfigToDisk(VespaTlsConfig.tlsDisabled());
    }

    @Test
    public void jute_max_buffer_can_be_set() throws IOException {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.myid(0);
        File idFile = folder.newFile();
        File cfgFile = folder.newFile();

        builder.server(new ZookeeperServerConfig.Server.Builder().id(0).hostname("testhost"));
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.myidFile(idFile.getAbsolutePath());

        new Configurator(builder.build()).writeConfigToDisk(VespaTlsConfig.tlsDisabled());
        assertEquals("" + new ZookeeperServerConfig(builder).juteMaxBuffer(), System.getProperty(ZOOKEEPER_JUTE_MAX_BUFFER));

        final int max_buffer = 1;
        builder.juteMaxBuffer(max_buffer);
        new Configurator(builder.build()).writeConfigToDisk(VespaTlsConfig.tlsDisabled());
        assertEquals("" + max_buffer, System.getProperty(ZOOKEEPER_JUTE_MAX_BUFFER));
    }

    private ZookeeperServerConfig.Builder createConfigBuilderForSingleHost(File cfgFile, File idFile) {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.myidFile(idFile.getAbsolutePath());
        builder.server(newServer(0, "foo", 123, 321, false));
        builder.myid(0);
        builder.tickTime(1234);
        return builder;
    }

    private ZookeeperServerConfig.Server.Builder newServer(int id, String hostName, int electionPort, int quorumPort, boolean joining) {
        ZookeeperServerConfig.Server.Builder builder = new ZookeeperServerConfig.Server.Builder();
        builder.id(id);
        builder.hostname(hostName);
        builder.electionPort(electionPort);
        builder.quorumPort(quorumPort);
        builder.joining(joining);
        return builder;
    }

    private void validateIdFile(File idFile, String expected) {
        String actual = Exceptions.uncheck(() -> Files.readString(idFile.toPath()));
        assertEquals(expected, actual);
    }

    private String commonConfig() {
        return "tickTime=1234\n" +
               "initLimit=20\n" +
               "syncLimit=15\n" +
               "maxClientCnxns=0\n" +
               "snapCount=50000\n" +
               "dataDir=" + getDefaults().underVespaHome("var/zookeeper") + "\n" +
               "autopurge.purgeInterval=1\n" +
               "autopurge.snapRetainCount=15\n" +
               "4lw.commands.whitelist=conf,cons,crst,dirs,dump,envi,mntr,ruok,srst,srvr,stat,wchs\n" +
               "admin.enableServer=false\n" +
               "serverCnxnFactory=org.apache.zookeeper.server.VespaNettyServerCnxnFactory\n" +
               "quorumListenOnAllIPs=true\n" +
               "standaloneEnabled=false\n" +
               "reconfigEnabled=true\n" +
               "skipACL=yes\n";
    }

    private void validateConfigFileSingleHost(File cfgFile) {
        String expected =
                commonConfig() +
                "server.0=foo:321:123;2181\n" +
                "sslQuorum=false\n" +
                "portUnification=false\n" +
                "client.portUnification=false\n";
        validateConfigFile(cfgFile, expected);
    }

    private String tlsQuorumConfig() {
        return "ssl.quorum.context.supplier.class=com.yahoo.vespa.zookeeper.VespaSslContextProvider\n" +
                "ssl.quorum.ciphersuites=TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384,TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256," +
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384\n" +
                "ssl.quorum.enabledProtocols=TLSv1.2\n" +
                "ssl.quorum.clientAuth=NEED\n";
    }

    private String tlsClientServerConfig() {
        return "ssl.context.supplier.class=com.yahoo.vespa.zookeeper.VespaSslContextProvider\n" +
                "ssl.ciphersuites=TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384,TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256," +
                "TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384\n" +
                "ssl.enabledProtocols=TLSv1.2\n" +
                "ssl.clientAuth=NEED\n";
    }

    private void validateConfigFileMultipleHosts(File cfgFile) {
        String expected =
                commonConfig() +
                "server.0=foo:321:123;2181\n" +
                "server.1=bar:432:234;2181\n" +
                "server.2=baz:543:345:observer;2181\n" +
                "sslQuorum=false\n" +
                "portUnification=false\n" +
                "client.portUnification=false\n";
        validateConfigFile(cfgFile, expected);
    }


    private void validateConfigFileTlsWithMixedMode(File cfgFile) {
        String expected =
                commonConfig() +
                "server.0=foo:321:123;2181\n" +
                "sslQuorum=true\n" +
                "portUnification=true\n" +
                tlsQuorumConfig() +
                "client.portUnification=true\n" +
                tlsClientServerConfig();
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFileTlsWithoutMixedMode(File cfgFile) {
        String expected =
                commonConfig() +
                "server.0=foo:321:123;2181\n" +
                "sslQuorum=true\n" +
                "portUnification=false\n" +
                tlsQuorumConfig() +
                "client.portUnification=false\n" +
                tlsClientServerConfig();
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFile(File cfgFile, String expected) {
        String actual = Exceptions.uncheck(() -> Files.readString(cfgFile.toPath()));
        assertEquals(expected, actual);
    }

    private TlsContext createTlsContext() {
        KeyPair keyPair = KeyUtils.generateKeypair(EC);
        X509Certificate certificate = X509CertificateBuilder
                .fromKeypair(keyPair, new X500Principal("CN=dummy"), EPOCH, EPOCH.plus(1, DAYS), SHA256_WITH_ECDSA, BigInteger.ONE)
                .build();
        return new DefaultTlsContext(
                List.of(certificate), keyPair.getPrivate(), List.of(certificate), new AuthorizedPeers(Set.of()),
                AuthorizationMode.ENFORCE, PeerAuthentication.NEED, HostnameVerification.DISABLED);
    }

}
