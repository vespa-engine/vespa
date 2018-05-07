// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.system;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author arnej27959
 */
public class CatchSigTermTestCase {

    @Test
    public void testThatSetupCompiles() {
        CatchSigTerm.setup(new AtomicBoolean(false));
    }

}
