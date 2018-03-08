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
public class ZooKeeperServerTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void config_is_written_correctly_when_one_server() throws IOException {
        File cfgFile = folder.newFile();
        File idFile = folder.newFile();
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.myidFile(idFile.getAbsolutePath());
        builder.server(newServer(1, "foo", 123, 321));
        builder.myid(1);
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
        builder.server(newServer(1, "foo", 123, 321));
        builder.server(newServer(2, "bar", 234, 432));
        builder.server(newServer(3, "baz", 345, 543));
        builder.myidFile(idFile.getAbsolutePath());
        builder.myid(1);
        createServer(builder);
        validateConfigFileMultipleHosts(cfgFile);
        validateIdFile(idFile, "1\n");
    }

    private void createServer(ZookeeperServerConfig.Builder builder) {
        new ZooKeeperServer(new ZookeeperServerConfig(builder), false);
    }

    @Test(expected = RuntimeException.class)
    public void require_that_this_id_must_be_present_amongst_servers() throws IOException {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.server(newServer(2, "bar", 234, 432));
        builder.server(newServer(3, "baz", 345, 543));
        builder.myid(1);
        createServer(builder);
    }

    @Test
    public void juteMaxBufferCanBeSet() throws IOException {
        ZookeeperServerConfig.Builder builder = new ZookeeperServerConfig.Builder();
        builder.myid(1);
        File idFile = folder.newFile();
        File cfgFile = folder.newFile();

        builder.server(new ZookeeperServerConfig.Server.Builder().id(0).hostname("testhost"));
        builder.zooKeeperConfigFile(cfgFile.getAbsolutePath());
        builder.myidFile(idFile.getAbsolutePath());

        createServer(builder);
        assertThat(System.getProperty(ZooKeeperServer.ZOOKEEPER_JUTE_MAX_BUFFER), is("" + new ZookeeperServerConfig(builder).juteMaxBuffer()));

        final int max_buffer = 1;
        builder.juteMaxBuffer(max_buffer);
        createServer(builder);
        assertThat(System.getProperty(ZooKeeperServer.ZOOKEEPER_JUTE_MAX_BUFFER), is("" + max_buffer));
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

    private void validateConfigFileSingleHost(File cfgFile) throws IOException {
        String expected =
            "tickTime=2000\n" +
            "initLimit=20\n" +
            "syncLimit=15\n" +
            "maxClientCnxns=0\n" +
            "snapCount=50000\n" +
            "dataDir=" + getDefaults().underVespaHome("var/zookeeper") + "\n" +
            "clientPort=2181\n" +
            "autopurge.purgeInterval=1\n" +
            "autopurge.snapRetainCount=15\n";
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFileMultipleHosts(File cfgFile) throws IOException {
        String expected =
                "tickTime=2000\n" +
                        "initLimit=20\n" +
                        "syncLimit=15\n" +
                        "maxClientCnxns=0\n" +
                        "snapCount=50000\n" +
                        "dataDir=" + getDefaults().underVespaHome("var/zookeeper") + "\n" +
                        "clientPort=2181\n" +
                        "autopurge.purgeInterval=1\n" +
                        "autopurge.snapRetainCount=15\n" +
                        "server.1=foo:321:123\n" +
                        "server.2=bar:432:234\n" +
                        "server.3=baz:543:345\n";
        validateConfigFile(cfgFile, expected);
    }

    private void validateConfigFile(File cfgFile, String expected) throws IOException {
        String actual = IOUtils.readFile(cfgFile);
        assertThat(actual, is(expected));
    }
}
