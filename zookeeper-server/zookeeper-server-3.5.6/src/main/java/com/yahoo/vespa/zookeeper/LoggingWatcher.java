// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.zookeeper;

import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;

import java.util.logging.Level;
import java.util.logging.Logger;

class LoggingWatcher implements Watcher {

    private static final Logger log = java.util.logging.Logger.getLogger(LoggingWatcher.class.getName());

    @Override
    public void process(WatchedEvent event) {
        log.log(Level.INFO, event.toString());
    }

}
