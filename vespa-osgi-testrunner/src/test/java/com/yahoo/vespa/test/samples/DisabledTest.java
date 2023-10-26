// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Expect(skipped = 1, status = 2)
public class DisabledTest {

    @Test
    @Disabled("because")
    void test() { }

}
