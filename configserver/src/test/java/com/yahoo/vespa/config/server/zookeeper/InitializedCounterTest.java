// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class InitializedCounterTest {

    @Test
    public void requireThatCounterIsInitializedFromNumberOfSessions() {
        ConfigCurator configCurator = ConfigCurator.create(new MockCurator());
        configCurator.createNode("/sessions");
        configCurator.createNode("/sessions/1");
        configCurator.createNode("/sessions/2");

        InitializedCounter counter = new InitializedCounter(configCurator, "/counter", "/sessions");
        assertThat(counter.counter.get(), is(2l));
    }

}
