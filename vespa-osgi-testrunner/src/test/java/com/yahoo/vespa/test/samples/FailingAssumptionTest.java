package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Expect(aborted = 1, status = 2)
public class FailingAssumptionTest {

    @Test
    void test() { assumeTrue(false, "assumption"); }

}
