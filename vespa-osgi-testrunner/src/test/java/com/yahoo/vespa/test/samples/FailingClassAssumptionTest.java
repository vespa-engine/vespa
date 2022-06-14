package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

@Expect(aborted = 2, status = 2)
public class FailingClassAssumptionTest {

    { Assumptions.assumeTrue(false, "assumption"); }

    @Test
    void test() { }

    @Test
    void fest() { }

}

