// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.logserver.handlers.archive;

import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @author Bjorn Borud
 */
public class LogWriterLRUCache extends LinkedHashMap<Integer, LogWriter> {
    private static final Logger log = Logger.getLogger(LogWriterLRUCache.class.getName());

    final int maxEntries = 5;

    public LogWriterLRUCache(int initialCapacity, float loadFactor) {
        super(initialCapacity, loadFactor, true);
    }

    // TODO: implement unit test for this
    protected boolean removeEldestEntry(Map.Entry<Integer, LogWriter> eldest) {
        if (size() > maxEntries) {
            LogWriter logWriter = eldest.getValue();
            log.fine("Closing oldest LogWriter: " + logWriter);
            try {
                logWriter.close();
            } catch (IOException e) {
                log.log(Level.WARNING, "closing LogWriter failed", e);
            }
            return true;
        }

        return false;
    }
}
