// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

@Expect(failed = 2, status = 4)
public class FailingInstantiationAssertionTest {

    { fail(); }

    @Test
    void test() { }

    @Test
    void fest() { }

}
