// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertNotNull;


/**
 * @author Simon Thoresen Hult
 */
public class CurrentContainerTestCase {

    @Test
    void requireThatNewRequestsCreateSnapshot() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        Request request = new Request(driver, URI.create("http://host/path"));
        assertNotNull(request.container());
        request.release();
        driver.close();
    }
}
