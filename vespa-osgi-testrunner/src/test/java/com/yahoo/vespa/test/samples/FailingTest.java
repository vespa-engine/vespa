package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Test;

@Expect(error = 1, status = 5)
public class FailingTest {

    @Test
    void test() { throw new RuntimeException(); }

}
