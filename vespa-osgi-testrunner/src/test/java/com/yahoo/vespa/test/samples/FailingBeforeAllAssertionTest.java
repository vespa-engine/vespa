// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Expect(skipped = 2, status = 4)
public class FailingBeforeAllAssertionTest {

    @BeforeAll
    static void fail() { Assertions.fail(); }

    @Test
    void test() { }

    @Test
    void fest() { }

}
