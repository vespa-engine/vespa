// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;

import java.net.URI;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;


/**
 * @author Simon Thoresen Hult
 */
public class BindingNotFoundTestCase {

    @Test
    public void requireThatAccessorsWork() {
        URI uri = URI.create("http://host/path");
        BindingNotFoundException e = new BindingNotFoundException(uri);
        assertEquals(uri, e.uri());
    }

    @Test
    public void requireThatBindingNotFoundIsThrown() {
        TestDriver driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        driver.activateContainer(driver.newContainerBuilder());
        Request request = new Request(driver, URI.create("http://host/path"));
        try {
            request.connect(new MyResponseHandler());
            fail();
        } catch (BindingNotFoundException e) {
            assertEquals(request.getUri(), e.uri());
        }
        request.release();
        driver.close();
    }

    private class MyResponseHandler implements ResponseHandler {

        @Override
        public ContentChannel handleResponse(Response response) {
            return null;
        }
    }
}
