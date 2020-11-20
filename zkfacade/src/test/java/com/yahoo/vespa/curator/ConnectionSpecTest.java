// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator;

import com.yahoo.net.HostName;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class ConnectionSpecTest {

    @Test
    public void create() {
        HostName.setHostNameForTestingOnly("host2");
        Config config = new Config(List.of(new Config.Server("host1", 10001),
                                           new Config.Server("host2", 10002),
                                           new Config.Server("host3", 10003)));

        {
            ConnectionSpec spec = ConnectionSpec.create(config.servers, Config.Server::hostname, Config.Server::port, false);
            assertEquals("host1:10001,host2:10002,host3:10003", spec.local());
            assertEquals("host1:10001,host2:10002,host3:10003", spec.ensemble());
            assertEquals(3, spec.ensembleSize());
        }

        {
            ConnectionSpec specLocalAffinity = ConnectionSpec.create(config.servers, Config.Server::hostname, Config.Server::port, true);
            assertEquals("host2:10002", specLocalAffinity.local());
            assertEquals("host1:10001,host2:10002,host3:10003", specLocalAffinity.ensemble());
            assertEquals(3, specLocalAffinity.ensembleSize());
        }

        {
            ConnectionSpec specFromString = ConnectionSpec.create("host1:10001", "host1:10001,host2:10002");
            assertEquals("host1:10001", specFromString.local());
            assertEquals("host1:10001,host2:10002", specFromString.ensemble());
            assertEquals(2, specFromString.ensembleSize());
        }
    }

    private static class Config {

        private final List<Server> servers;

        public Config(List<Server> servers) {
            this.servers = servers;
        }

        private static class Server {

            private final String hostname;
            private final int port;

            public Server(String hostname, int port) {
                this.hostname = hostname;
                this.port = port;
            }

            public String hostname() {
                return hostname;
            }

            public int port() {
                return port;
            }
        }

    }

}
