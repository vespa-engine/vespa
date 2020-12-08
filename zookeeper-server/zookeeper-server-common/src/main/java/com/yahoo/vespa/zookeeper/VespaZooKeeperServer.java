// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import java.nio.file.Path;

/**
 * Interface for a component that writes zookeeper config and starts a zookeeper server.
 *
 * @author Harald Musum
 */
public interface VespaZooKeeperServer {

    void start(Path configFilePath);

}
