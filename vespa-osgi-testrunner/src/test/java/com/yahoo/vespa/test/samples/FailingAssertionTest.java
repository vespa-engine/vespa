package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Expect(failure = 1)
public class FailingAssertionTest {

    @Test
    void fail() { Assertions.fail(); }

}
