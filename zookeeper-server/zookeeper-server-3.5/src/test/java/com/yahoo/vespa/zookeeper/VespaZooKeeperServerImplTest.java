// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * Tests the zookeeper server.
 */
public class VespaZooKeeperServerImplTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void config_is_written_correctly_when_one_server() throws IOException {
        File cfgFile = folder.newFile();
        File idFile = folder.newFile();
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile);
        createServer(builder);
        validateConfigFileSingleHost(cfgFile);
        validateIdFile(idFile, "");
    }

    @Test
    public void config_is_written_correctly_when_multiple_servers() throws IOException {
        File cfgFile = folder.newFile();
        File idFile = folder.newFile();
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.server(newServer(0, "foo", 123, 321));
        builder.server(newServer(1, "bar", 234, 432));
        builder.server(newServer(2, "baz", 345, 543));
        builder.myidFile(idFile.getAbsolutePath());
        builder.myid(1);
        createServer(builder);
        validateConfigFileMultipleHosts(cfgFile);
        validateIdFile(idFile, "1\n");
    }

    @Test
    public void config_is_written_correctly_with_tls_for_quorum_communication_port_unification() throws IOException {
        File cfgFile = folder.newFile();
        File idFile = folder.newFile();
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile);
        builder.tlsForQuorumCommunication(ZookeeperServerConfig.TlsForQuorumCommunication.Enum.PORT_UNIFICATION);
        createServer(builder);
        validateConfigFilePortUnification(cfgFile);
    }

    @Test
    public void config_is_written_correctly_with_tls_for_quorum_communication_tls_with_port_unification() throws IOException {
        File cfgFile = folder.newFile();
        File idFile = folder.newFile();
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile);
        builder.tlsForQuorumCommunication(ZookeeperServerConfig.TlsForQuorumCommunication.Enum.TLS_WITH_PORT_UNIFICATION);
        createServer(builder);
        validateConfigFileTlsWithPortUnification(cfgFile);
    }

    @Test
    public void config_is_written_correctly_with_tls_for_quorum_communication_tls_only() throws IOException {
        File cfgFile = folder.newFile();
        File idFile = folder.newFile();
        ZookeeperServerConfig.Builder builder = createConfigBuilderForSingleHost(cfgFile, idFile);
        builder.tlsForQuorumCommunication(ZookeeperServerConfig.TlsForQuorumCommunication.Enum.TLS_ONLY);
        createServer(builder);
        validateConfigFileTlsOnly(cfgFile);
    }

    private ZookeeperServerConfig.Builder createConfigBuilderForSingleHost(File cfgFile, File idFile) {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.myidFile(idFile.getAbsolutePath());
        builder.server(newServer(0, "foo", 123, 321));
        builder.myid(0);
        return builder;
    }

    private void createServer(ZookeeperServerConfig.Builder builder) {
        new VespaZooKeeperServerImpl(new ZookeeperServerConfig(builder), false);
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
               "ssl.quorum.ciphersuites=TLS_AES_128_GCM_SHA256,TLS_AES_256_GCM_SHA384,TLS_CHACHA20_POLY1305_SHA256,TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256\n" +
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

    private void validateConfigFilePortUnification(File cfgFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                commonTlsConfig() +
                "sslQuorum=false\n" +
                "portUnification=true\n";
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFileTlsWithPortUnification(File cfgFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                commonTlsConfig() +
                "sslQuorum=true\n" +
                "portUnification=true\n";
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFileTlsOnly(File cfgFile) throws IOException {
        String expected =
                commonConfig() +
                "server.0=foo:321:123\n" +
                commonTlsConfig() +
                "sslQuorum=true\n" +
                "portUnification=false\n";
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFile(File cfgFile, String expected) throws IOException {
        String actual = IOUtils.readFile(cfgFile);
        assertThat(actual, is(expected));
    }
}
