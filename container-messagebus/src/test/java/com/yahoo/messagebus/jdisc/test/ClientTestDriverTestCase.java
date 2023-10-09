// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc.test;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ClientTestDriverTestCase {

    @Test
    public void requireThatFactoryMethodsWork() throws ListenFailedException {
        ClientTestDriver driver = ClientTestDriver.newInstance();
        assertNotNull(driver);
        assertTrue(driver.close());

        driver = ClientTestDriver.newInstanceWithProtocol(new SimpleProtocol());
        assertNotNull(driver);
        assertTrue(driver.close());
    }

}
