// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.service.ServerProvider;
import org.junit.Test;

/**
 * @author Simon Thoresen Hult
 */
public class NonWorkingServerTestCase {

    @Test
    public void requireThatStartDoesNotThrow() {
        ServerProvider server = new NonWorkingServerProvider();
        server.start();
    }

    @Test
    public void requireThatCloseDoesNotThrow() {
        ServerProvider server = new NonWorkingServerProvider();
        server.close();
    }

    @Test
    public void requireThatReferDoesNotThrow() {
        ServerProvider server = new NonWorkingServerProvider();
        server.refer();
    }

    @Test
    public void requireThatReleaseDoesNotThrow() {
        ServerProvider server = new NonWorkingServerProvider();
        server.release();
    }
}
