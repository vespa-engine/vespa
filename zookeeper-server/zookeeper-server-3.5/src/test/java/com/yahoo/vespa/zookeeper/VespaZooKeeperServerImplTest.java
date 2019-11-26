// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

import static com.yahoo.cloud.config.ZookeeperServerConfig.TlsForQuorumCommunication.Enum.OFF;
import static com.yahoo.cloud.config.ZookeeperServerConfig.TlsForQuorumCommunication.Enum.PORT_UNIFICATION;
import static com.yahoo.cloud.config.ZookeeperServerConfig.TlsForQuorumCommunication.Enum.TLS_ONLY;
import static com.yahoo.cloud.config.ZookeeperServerConfig.TlsForQuorumCommunication.Enum.TLS_WITH_PORT_UNIFICATION;
import static com.yahoo.security.KeyAlgorithm.EC;
import static com.yahoo.security.SignatureAlgorithm.SHA256_WITH_ECDSA;
import static java.time.Instant.EPOCH;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import static org.junit.Assert.assertTrue;

/**
 * Tests the zookeeper server.
 */
public class VespaZooKeeperServerImplTest {

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
        builder.tlsForQuorumCommunication(OFF);
        createServer(builder);
        validateConfigFileSingleHost(cfgFile);
        validateIdFile(idFile, "");
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
        builder.tlsForQuorumCommunication(OFF);
        createServer(builder);
        validateConfigFileMultipleHosts(cfgFile);
        validateIdFile(idFile, "1\n");
    }

    @Test
    public void config_is_written_correctly_with_tls_for_quorum_communication_port_unification() throws IOException {
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile, jksKeyStoreFile);
        builder.tlsForQuorumCommunication(PORT_UNIFICATION);
        Optional<TransportSecurityOptions> transportSecurityOptions = createTransportSecurityOptions();
        createServer(builder, transportSecurityOptions);
        validateConfigFilePortUnification(cfgFile, jksKeyStoreFile, transportSecurityOptions.get().getCaCertificatesFile().get().toFile());
        validateThatJksKeyStoreFileExists(jksKeyStoreFile);
    }

    @Test
    public void config_is_written_correctly_with_tls_for_quorum_communication_tls_with_port_unification() throws IOException {
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile, jksKeyStoreFile);
        builder.tlsForQuorumCommunication(TLS_WITH_PORT_UNIFICATION);
        Optional<TransportSecurityOptions> transportSecurityOptions = createTransportSecurityOptions();
        createServer(builder, transportSecurityOptions);
        validateConfigFileTlsWithPortUnification(cfgFile, jksKeyStoreFile, transportSecurityOptions.get().getCaCertificatesFile().get().toFile());
        validateThatJksKeyStoreFileExists(jksKeyStoreFile);
    }

    @Test
    public void config_is_written_correctly_with_tls_for_quorum_communication_tls_only() throws IOException {
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile, jksKeyStoreFile);
        builder.tlsForQuorumCommunication(TLS_ONLY);
        Optional<TransportSecurityOptions> transportSecurityOptions = createTransportSecurityOptions();
        createServer(builder, transportSecurityOptions);
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

    private void createServer(ZookeeperServerConfig.Builder builder) {
        createServer(builder, Optional.empty());
    }

    private void createServer(ZookeeperServerConfig.Builder builder, Optional<TransportSecurityOptions> options) {
        new VespaZooKeeperServerImpl(new ZookeeperServerConfig(builder), false, options);
    }

    @Test(expected = RuntimeException.class)
    public void require_that_this_id_must_be_present_amongst_servers() {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.server(newServer(1, "bar", 234, 432));
        builder.server(newServer(2, "baz", 345, 543));
        builder.myid(0);
        createServer(builder);
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
        builder.tlsForQuorumCommunication(OFF);

        createServer(builder);
        assertThat(System.getProperty(VespaZooKeeperServerImpl.ZOOKEEPER_JUTE_MAX_BUFFER), is("" + new ZookeeperServerConfig(builder).juteMaxBuffer()));

        final int max_buffer = 1;
        builder.juteMaxBuffer(max_buffer);
        createServer(builder);
        assertThat(System.getProperty(VespaZooKeeperServerImpl.ZOOKEEPER_JUTE_MAX_BUFFER), is("" + max_buffer));
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
        assertThat(actual, is(expected));
    }

    private String commonConfig() {
        return "tickTime=2000\n" +
               "initLimit=20\n" +
               "syncLimit=15\n" +
               "maxClientCnxns=0\n" +
               "snapCount=50000\n" +
               "dataDir=" + getDefaults().underVespaHome("var/zookeeper") + "\n" +
               "clientPort=2181\n" +
               "autopurge.purgeInterval=1\n" +
               "autopurge.snapRetainCount=15\n" +
               "4lw.commands.whitelist=conf,cons,crst,dirs,dump,envi,mntr,ruok,srst,srvr,stat,wchs\n" +
               "admin.enableServer=false\n" +
               "serverCnxnFactory=org.apache.zookeeper.server.NettyServerCnxnFactory\n";
    }

    private String keyStoreAndTrustStoreConfig(File jksKeyStoreFilePath, File caCertificatesFilePath) {
        StringBuilder sb = new StringBuilder();

        sb.append("ssl.quorum.keyStore.location=").append(jksKeyStoreFilePath.getAbsolutePath()).append("\n");
        sb.append("ssl.quorum.keyStore.type=JKS\n");
        sb.append("ssl.quorum.trustStore.location=").append(caCertificatesFilePath.getAbsolutePath()).append("\n");
        sb.append("ssl.quorum.trustStore.type=PEM\n");
        return sb.toString();
    }

    private void validateConfigFileSingleHost(File cfgFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                commonTlsConfig() +
                "sslQuorum=false\n" +
                "portUnification=false\n";
        validateConfigFile(cfgFile, expected);
    }

    private String commonTlsConfig() {
        return "ssl.quorum.hostnameVerification=false\n" +
               "ssl.quorum.clientAuth=NEED\n" +
               "ssl.quorum.ciphersuites=TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384,TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384\n" +
               "ssl.quorum.enabledProtocols=TLSv1.2\n" +
               "ssl.quorum.protocol=TLSv1.2\n";
    }

    private void validateConfigFileMultipleHosts(File cfgFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                "server.1=bar:432:234\n" +
                "server.2=baz:543:345\n" +
                commonTlsConfig() +
                "sslQuorum=false\n" +
                "portUnification=false\n";
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFilePortUnification(File cfgFile, File jksKeyStoreFile, File caCertificatesFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                commonTlsConfig() +
                "sslQuorum=false\n" +
                "portUnification=true\n" +
                keyStoreAndTrustStoreConfig(jksKeyStoreFile, caCertificatesFile);
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFileTlsWithPortUnification(File cfgFile, File jksKeyStoreFile, File caCertificatesFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                commonTlsConfig() +
                "sslQuorum=true\n" +
                "portUnification=true\n" +
                keyStoreAndTrustStoreConfig(jksKeyStoreFile, caCertificatesFile);
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFileTlsOnly(File cfgFile, File jksKeyStoreFile, File caCertificatesFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                commonTlsConfig() +
                "sslQuorum=true\n" +
                "portUnification=false\n" +
                keyStoreAndTrustStoreConfig(jksKeyStoreFile, caCertificatesFile);
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFile(File cfgFile, String expected) throws IOException {
        String actual = IOUtils.readFile(cfgFile);
        assertThat(actual, is(expected));
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
