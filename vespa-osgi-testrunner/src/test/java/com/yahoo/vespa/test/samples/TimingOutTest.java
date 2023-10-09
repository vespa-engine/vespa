// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

@Expect(error = 1, status = 5)
public class TimingOutTest {

    @Test
    @Timeout(value = 1, unit = TimeUnit.MILLISECONDS)
    void test() throws InterruptedException {
        Thread.sleep(10_000);
    }

}
