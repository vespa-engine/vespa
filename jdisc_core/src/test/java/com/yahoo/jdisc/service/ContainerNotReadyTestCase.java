// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.service;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.fail;


/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class ContainerNotReadyTestCase {

    @Test
    public void requireThatExceptionIsThrown() throws BindingSetNotFoundException {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        try {
            driver.newReference(URI.create("http://host"));
            fail();
        } catch (ContainerNotReadyException e) {

        }
        driver.close();
    }
}
