// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.system;

import com.yahoo.system.CatchSigTerm;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author arnej27959
 */
public class CatchSigTermTestCase extends junit.framework.TestCase {

    public CatchSigTermTestCase(String name) {
        super(name);
    }

    public void testThatSetupCompiles() {
        CatchSigTerm.setup(new AtomicBoolean(false));
    }
}
