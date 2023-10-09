// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Test;

@Expect(error = 2, status = 5)
public class FailingInstantiationTest {

    final int i = Integer.parseInt("");

    @Test
    void test() { }

    @Test
    void fest() { }

}
