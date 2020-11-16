// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.io.IOUtils;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.X509CertificateBuilder;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.security.tls.TransportSecurityOptions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.security.auth.x500.X500Principal;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.cert.X509Certificate;
import java.util.Optional;

import static com.yahoo.cloud.config.ZookeeperServerConfig.TlsForQuorumCommunication;
import static com.yahoo.cloud.config.ZookeeperServerConfig.TlsForClientServerCommunication;
import static com.yahoo.security.KeyAlgorithm.EC;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static com.yahoo.vespa.zookeeper.Configurator.ZOOKEEPER_JUTE_MAX_BUFFER;
import static java.time.Instant.EPOCH;
import static java.time.temporal.ChronoUnit.DAYS;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests the zookeeper server.
 */
public class ConfiguratorTest {

    private File cfgFile;
    private File idFile;
    private File jksKeyStoreFile;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        cfgFile = folder.newFile();
        idFile = folder.newFile();
        jksKeyStoreFile = folder.newFile();
    }

    @Test
    public void config_is_written_correctly_when_one_server() throws IOException {
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile, jksKeyStoreFile);
        new Configurator(builder.build()).writeConfigToDisk(Optional.empty());
        validateConfigFileSingleHost(cfgFile);
        validateIdFile(idFile, "0\n");
    }

    @Test
    public void config_is_written_correctly_when_multiple_servers() throws IOException {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.server(newServer(0, "foo", 123, 321));
        builder.server(newServer(1, "bar", 234, 432));
        builder.server(newServer(2, "baz", 345, 543));
        builder.myidFile(idFile.getAbsolutePath());
        builder.myid(1);
        new Configurator(builder.build()).writeConfigToDisk(Optional.empty());
        validateConfigFileMultipleHosts(cfgFile);
        validateIdFile(idFile, "1\n");
    }

    @Test
    public void config_is_written_correctly_with_tls_for_quorum_communication_port_unification() throws IOException {
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile, jksKeyStoreFile);
        builder.tlsForQuorumCommunication(TlsForQuorumCommunication.PORT_UNIFICATION);
        builder.tlsForClientServerCommunication(TlsForClientServerCommunication.PORT_UNIFICATION);
        Optional<TransportSecurityOptions> transportSecurityOptions = createTransportSecurityOptions();
        new Configurator(builder.build()).writeConfigToDisk(transportSecurityOptions);
        validateConfigFilePortUnification(cfgFile, jksKeyStoreFile, transportSecurityOptions.get().getCaCertificatesFile().get().toFile());
        validateThatJksKeyStoreFileExists(jksKeyStoreFile);
    }

    @Test
    public void config_is_written_correctly_with_tls_for_quorum_communication_tls_with_port_unification() throws IOException {
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile, jksKeyStoreFile);
        builder.tlsForQuorumCommunication(TlsForQuorumCommunication.TLS_WITH_PORT_UNIFICATION);
        builder.tlsForClientServerCommunication(TlsForClientServerCommunication.TLS_WITH_PORT_UNIFICATION);
        Optional<TransportSecurityOptions> transportSecurityOptions = createTransportSecurityOptions();
        new Configurator(builder.build()).writeConfigToDisk(transportSecurityOptions);
        validateConfigFileTlsWithPortUnification(cfgFile, jksKeyStoreFile, transportSecurityOptions.get().getCaCertificatesFile().get().toFile());
        validateThatJksKeyStoreFileExists(jksKeyStoreFile);
    }

    @Test
    public void config_is_written_correctly_with_tls_for_quorum_communication_tls_only() throws IOException {
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile, jksKeyStoreFile);
        builder.tlsForQuorumCommunication(TlsForQuorumCommunication.TLS_ONLY);
        builder.tlsForClientServerCommunication(TlsForClientServerCommunication.TLS_ONLY);
        Optional<TransportSecurityOptions> transportSecurityOptions = createTransportSecurityOptions();
        new Configurator(builder.build()).writeConfigToDisk(transportSecurityOptions);
        validateConfigFileTlsOnly(cfgFile, jksKeyStoreFile, transportSecurityOptions.get().getCaCertificatesFile().get().toFile());
        validateThatJksKeyStoreFileExists(jksKeyStoreFile);
    }

    private ZookeeperServerConfig.Builder createConfigBuilderForSingleHost(File cfgFile, File idFile, File jksKeyStoreFile) {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.myidFile(idFile.getAbsolutePath());
        builder.server(newServer(0, "foo", 123, 321));
        builder.myid(0);
        builder.jksKeyStoreFile(jksKeyStoreFile.getAbsolutePath());
        return builder;
    }

    @Test(expected = RuntimeException.class)
    public void require_that_this_id_must_be_present_amongst_servers() {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.server(newServer(1, "bar", 234, 432));
        builder.server(newServer(2, "baz", 345, 543));
        builder.myid(0);
        new Configurator(builder.build()).writeConfigToDisk(Optional.empty());
    }

    @Test
    public void juteMaxBufferCanBeSet() throws IOException {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.myid(0);
        File idFile = folder.newFile();
        File cfgFile = folder.newFile();

        builder.server(new ZookeeperServerConfig.Server.Builder().id(0).hostname("testhost"));
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.myidFile(idFile.getAbsolutePath());

        new Configurator(builder.build()).writeConfigToDisk(Optional.empty());
        assertEquals("" + new ZookeeperServerConfig(builder).juteMaxBuffer(), System.getProperty(ZOOKEEPER_JUTE_MAX_BUFFER));

        final int max_buffer = 1;
        builder.juteMaxBuffer(max_buffer);
        new Configurator(builder.build()).writeConfigToDisk(Optional.empty());
        assertEquals("" + max_buffer, System.getProperty(ZOOKEEPER_JUTE_MAX_BUFFER));
    }

    private ZookeeperServerConfig.Server.Builder newServer(int id, String hostName, int electionPort, int quorumPort) {
        ZookeeperServerConfig.Server.Builder builder = new ZookeeperServerConfig.Server.Builder();
        builder.id(id);
        builder.hostname(hostName);
        builder.electionPort(electionPort);
        builder.quorumPort(quorumPort);
        return builder;
    }

    private void validateIdFile(File idFile, String expected) throws IOException {
        String actual = IOUtils.readFile(idFile);
        assertEquals(expected, actual);
    }

    private String commonConfig() {
        return "tickTime=2000\n" +
               "initLimit=20\n" +
               "syncLimit=15\n" +
               "maxClientCnxns=0\n" +
               "snapCount=50000\n" +
               "dataDir=" + getDefaults().underVespaHome("var/zookeeper") + "\n" +
               "clientPort=2181\n" +
               "secureClientPort=2184\n" +
               "autopurge.purgeInterval=1\n" +
               "autopurge.snapRetainCount=15\n" +
               "4lw.commands.whitelist=conf,cons,crst,dirs,dump,envi,mntr,ruok,srst,srvr,stat,wchs\n" +
               "admin.enableServer=false\n" +
               "serverCnxnFactory=org.apache.zookeeper.server.NettyServerCnxnFactory\n" +
               "quorumListenOnAllIPs=true\n" +
               "standaloneEnabled=false\n" +
               "reconfigEnabled=true\n";
    }

    private String quorumKeyStoreAndTrustStoreConfig(File jksKeyStoreFilePath, File caCertificatesFilePath) {
        StringBuilder sb = new StringBuilder();

        sb.append("ssl.quorum.keyStore.location=").append(jksKeyStoreFilePath.getAbsolutePath()).append("\n");
        sb.append("ssl.quorum.keyStore.type=JKS\n");
        sb.append("ssl.quorum.trustStore.location=").append(caCertificatesFilePath.getAbsolutePath()).append("\n");
        sb.append("ssl.quorum.trustStore.type=PEM\n");
        return sb.toString();
    }

    private String clientServerKeyStoreAndTrustStoreConfig(File jksKeyStoreFilePath, File caCertificatesFilePath) {
        StringBuilder sb = new StringBuilder();

        sb.append("ssl.keyStore.location=").append(jksKeyStoreFilePath.getAbsolutePath()).append("\n");
        sb.append("ssl.keyStore.type=JKS\n");
        sb.append("ssl.trustStore.location=").append(caCertificatesFilePath.getAbsolutePath()).append("\n");
        sb.append("ssl.trustStore.type=PEM\n");
        return sb.toString();
    }

    private void validateConfigFileSingleHost(File cfgFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                commonTlsQuorumConfig() +
                "sslQuorum=false\n" +
                "portUnification=false\n" +
                commonTlsClientServerConfig() +
                "client.portUnification=false\n";
        validateConfigFile(cfgFile, expected);
    }

    private String commonTlsQuorumConfig() {
        return "ssl.quorum.hostnameVerification=false\n" +
               "ssl.quorum.clientAuth=NEED\n" +
               "ssl.quorum.ciphersuites=TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384,TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384\n" +
               "ssl.quorum.enabledProtocols=TLSv1.2\n" +
               "ssl.quorum.protocol=TLS\n";
    }

    private String commonTlsClientServerConfig() {
        return "ssl.hostnameVerification=false\n" +
               "ssl.clientAuth=NEED\n" +
               "ssl.ciphersuites=TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384,TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384\n" +
               "ssl.enabledProtocols=TLSv1.2\n" +
               "ssl.protocol=TLS\n";
    }

    private void validateConfigFileMultipleHosts(File cfgFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                "server.1=bar:432:234\n" +
                "server.2=baz:543:345\n" +
                commonTlsQuorumConfig() +
                "sslQuorum=false\n" +
                "portUnification=false\n" +
                commonTlsClientServerConfig() +
                "client.portUnification=false\n";
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFilePortUnification(File cfgFile, File jksKeyStoreFile, File caCertificatesFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                commonTlsQuorumConfig() +
                "sslQuorum=false\n" +
                "portUnification=true\n" +
                quorumKeyStoreAndTrustStoreConfig(jksKeyStoreFile, caCertificatesFile) +
                commonTlsClientServerConfig() +
                "client.portUnification=true\n" +
                clientServerKeyStoreAndTrustStoreConfig(jksKeyStoreFile, caCertificatesFile);
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFileTlsWithPortUnification(File cfgFile, File jksKeyStoreFile, File caCertificatesFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                commonTlsQuorumConfig() +
                "sslQuorum=true\n" +
                "portUnification=true\n" +
                quorumKeyStoreAndTrustStoreConfig(jksKeyStoreFile, caCertificatesFile) +
                commonTlsClientServerConfig() +
                "client.portUnification=true\n" +
                clientServerKeyStoreAndTrustStoreConfig(jksKeyStoreFile, caCertificatesFile);
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFileTlsOnly(File cfgFile, File jksKeyStoreFile, File caCertificatesFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                commonTlsQuorumConfig() +
                "sslQuorum=true\n" +
                "portUnification=false\n" +
                quorumKeyStoreAndTrustStoreConfig(jksKeyStoreFile, caCertificatesFile) +
                commonTlsClientServerConfig() +
                "client.portUnification=false\n" +
                clientServerKeyStoreAndTrustStoreConfig(jksKeyStoreFile, caCertificatesFile);
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFile(File cfgFile, String expected) throws IOException {
        String actual = IOUtils.readFile(cfgFile);
        assertEquals(expected, actual);
    }

    private void validateThatJksKeyStoreFileExists(File cfgFile) {
        assertTrue(cfgFile.exists() && cfgFile.canRead());
    }

    private Optional<TransportSecurityOptions> createTransportSecurityOptions() throws IOException {
        KeyPair keyPair = KeyUtils.generateKeypair(EC);
        Path privateKeyFile = folder.newFile().toPath();
        Files.writeString(privateKeyFile, KeyUtils.toPem(keyPair.getPrivate()));

        X509Certificate certificate = X509CertificateBuilder
                .fromKeypair(keyPair, new X500Principal("CN=dummy"), EPOCH, EPOCH.plus(1, DAYS), SHA256_WITH_ECDSA, BigInteger.ONE)
                .build();
        Path certificateChainFile = folder.newFile().toPath();
        String certificatePem = X509CertificateUtils.toPem(certificate);
        Files.writeString(certificateChainFile, certificatePem);

        Path caCertificatesFile = folder.newFile().toPath();
        Files.writeString(caCertificatesFile, certificatePem);

        return Optional.of(new TransportSecurityOptions.Builder()
                                   .withCertificates(certificateChainFile, privateKeyFile)
                                   .withCaCertificates(caCertificatesFile)
                                   .build());
    }

}
