package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Test;

@Expect(successful = 1, status = 2)
public class SucceedingTest {

    @Test
    void test() { }

}

