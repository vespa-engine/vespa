// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Ulf Lilleengen
 */
public class InitializedCounterTest {

    @Test
    public void requireThatCounterIsInitializedFromNumberOfSessions() {
        Curator curator = new MockCurator();
        curator.create(Path.fromString("/sessions"));
        curator.create(Path.fromString("/sessions/1"));
        curator.create(Path.fromString("/sessions/2"));

        InitializedCounter counter = new InitializedCounter(curator, Path.fromString("/counter"), Path.fromString("/sessions"));
        assertEquals(2, counter.counter.get());
    }

}
