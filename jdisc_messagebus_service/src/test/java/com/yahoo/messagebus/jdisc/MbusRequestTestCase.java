// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.text.Utf8String;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.*;

/**
 * @author Simon Thoresen Hult
 */
public class MbusRequestTestCase {

    @Test
    public void requireThatAccessorsWork() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());

        MyMessage msg = new MyMessage();
        MbusRequest request = new MbusRequest(driver, URI.create("mbus://host/path"), msg);
        assertSame(msg, request.getMessage());
        request.release();
        driver.close();
    }

    @Test
    public void requireThatMessageCanNotBeNullInRootRequest() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        try {
            new MbusRequest(driver, URI.create("mbus://host/path"), null);
            fail();
        } catch (NullPointerException e) {
            // expected
        }
        assertTrue(driver.close());
    }

    @Test
    public void requireThatMessageCanNotBeNullInChildRequest() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        MbusRequest parent = new MbusRequest(driver, URI.create("mbus://host/path"), new SimpleMessage("foo"));
        try {
            new MbusRequest(parent, URI.create("mbus://host/path"), null);
            fail();
        } catch (NullPointerException e) {
            // expected
        }
        parent.release();
        assertTrue(driver.close());
    }

    private class MyMessage extends Message {

        @Override
        public Utf8String getProtocol() {
            return null;
        }

        @Override
        public int getType() {
            return 0;
        }
    }
}
