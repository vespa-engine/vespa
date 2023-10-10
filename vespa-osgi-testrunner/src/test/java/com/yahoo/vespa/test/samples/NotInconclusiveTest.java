// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.test.samples;

import ai.vespa.hosted.cd.InconclusiveTestException;
import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Test;

@Expect(error = 1, status = 5)
public class NotInconclusiveTest {

    @Test
    void test() { throw new InconclusiveTestException("soon"); }

}
