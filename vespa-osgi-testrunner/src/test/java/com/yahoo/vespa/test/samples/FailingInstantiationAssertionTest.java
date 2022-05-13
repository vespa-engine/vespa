package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

@Expect(failure = 2)
public class FailingInstantiationAssertionTest {

    { fail(); }

    @Test
    void test() { }

    @Test
    void fest() { }

}
