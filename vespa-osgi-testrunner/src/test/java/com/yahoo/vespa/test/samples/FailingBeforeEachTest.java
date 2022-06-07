package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@Expect(error = 1, status = 5)
public class FailingBeforeEachTest {

    @BeforeEach
    void fail() { throw new RuntimeException(); }

    @Test
    void test() { }

}
