// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Einar M R Rosenvinge
 */
public class NotAcceptingNewProcessingsTestCase {

    @Test
    public void testNotAccepting() {
        DocprocService service = new DocprocService("habla");
        service.setCallStack(new CallStack());
        service.setInService(true);

        service.process(new Processing());
        assertEquals(1, service.getQueueSize());

        service.setAcceptingNewProcessings(false);

        try {
            service.process(new Processing());
            fail("Should have gotten IllegalStateException here");
        } catch (IllegalStateException ise) {
            //ok!
        }
        assertEquals(1, service.getQueueSize());
    }

}
