// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.zookeeper;

import com.yahoo.vespa.config.server.TestWithCurator;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class InitializedCounterTest extends TestWithCurator {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Before
    public void setupZK() {
        configCurator.createNode("/sessions");
        configCurator.createNode("/sessions/1");
        configCurator.createNode("/sessions/2");
    }

    @Test
    public void requireThatCounterIsInitializedFromNumberOfSessions() {
        InitializedCounter counter = new InitializedCounter(configCurator, "/counter", "/sessions");
        assertThat(counter.counter.get(), is(2l));
    }

}
