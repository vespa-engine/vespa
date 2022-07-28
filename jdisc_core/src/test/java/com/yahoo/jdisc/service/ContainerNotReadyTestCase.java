// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class ContainerNotReadyTestCase {

    @Test
    void requireThatExceptionIsThrown() throws BindingSetNotFoundException {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        try {
            driver.newReference(URI.create("http://host"));
            fail();
        } catch (ContainerNotReadyException e) {

        }
        driver.close();
    }
}
