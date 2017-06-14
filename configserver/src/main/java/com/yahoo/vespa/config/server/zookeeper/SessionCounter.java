// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;

/**
 * A counter keeping track of session ids in an atomic fashion across multiple config servers.
 *
 * @author lulf
 * @since 5.1
 */
public class SessionCounter extends InitializedCounter {

    public SessionCounter(Curator curator, Path rootPath, Path sessionsDir) {
        super(curator, rootPath.append("sessionCounter").getAbsolute(), sessionsDir.getAbsolute());
    }

    /**
     * Atomically increment and return next session id.
     *
     * @return a new session id.
     */
    public long nextSessionId() {
        return counter.next();
    }
}
