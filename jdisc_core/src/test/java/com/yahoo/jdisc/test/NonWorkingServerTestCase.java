// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.service.ServerProvider;
import org.junit.jupiter.api.Test;

/**
 * @author Simon Thoresen Hult
 */
public class NonWorkingServerTestCase {

    @Test
    void requireThatStartDoesNotThrow() {
        ServerProvider server = new NonWorkingServerProvider();
        server.start();
    }

    @Test
    void requireThatCloseDoesNotThrow() {
        ServerProvider server = new NonWorkingServerProvider();
        server.close();
    }

    @Test
    void requireThatReferDoesNotThrow() {
        ServerProvider server = new NonWorkingServerProvider();
        server.refer();
    }

    @Test
    void requireThatReleaseDoesNotThrow() {
        ServerProvider server = new NonWorkingServerProvider();
        server.release();
    }
}
