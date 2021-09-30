// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.api;

import com.yahoo.path.Path;

import java.time.Duration;

/**
 * A client for a ZooKeeper cluster running inside Vespa. Applications that want to use ZooKeeper can inject this in
 * their code.
 *
 * @author mpolden
 */
public interface VespaCurator {

    /** Create and acquire a re-entrant lock in given path. This blocks until the lock is acquired or timeout elapses. */
    AutoCloseable lock(Path path, Duration timeout);

}
