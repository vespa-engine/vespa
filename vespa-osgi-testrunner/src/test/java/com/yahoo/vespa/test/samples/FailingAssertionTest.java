package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Expect(failed = 1, status = 4)
public class FailingAssertionTest {

    @Test
    void fail() { Assertions.fail(); }

}
