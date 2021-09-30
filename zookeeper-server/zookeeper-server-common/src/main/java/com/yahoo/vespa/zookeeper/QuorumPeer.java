// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Wraps ZK-version-dependent VespaQuorumPeer
 *
 * @author jonmv
 */
public interface QuorumPeer {

    /** Starts ZK with config from the given path. */
    void start(Path path);

    /** Shuts down this peer, with the given timeout, or kills the process. */
    void shutdown(Duration timeout);

}
