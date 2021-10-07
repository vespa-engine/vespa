// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.system;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author arnej27959
 */
public class CatchSignalsTestCase {

    @Test
    public void testThatSetupCompiles() {
        CatchSignals.setup(new AtomicBoolean(false));
    }

}
