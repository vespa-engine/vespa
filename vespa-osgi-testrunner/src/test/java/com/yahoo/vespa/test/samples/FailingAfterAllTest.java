// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

@Expect(successful = 2, status = 5)
public class FailingAfterAllTest {

    @AfterAll
    static void fail() { throw new RuntimeException(); }

    @Test
    void test() { }

    @Test
    void fest() { }

}
