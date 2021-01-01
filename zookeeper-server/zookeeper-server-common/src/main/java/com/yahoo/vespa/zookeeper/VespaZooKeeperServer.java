// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import java.nio.file.Path;

/**
 * Interface for a component that starts/stops a ZooKeeper server.
 *
 * @author hmusum
 */
public interface VespaZooKeeperServer {

    /** Shut down the server. Blocks until shutdown has completed */
    void shutdown();

    /** Start the server with the given config file */
    void start(Path configFilePath);

    /** Whether this server support dynamic reconfiguration */
    boolean reconfigurable();

}
