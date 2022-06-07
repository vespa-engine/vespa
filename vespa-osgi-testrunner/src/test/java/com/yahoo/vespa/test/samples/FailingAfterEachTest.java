package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

@Expect(error = 1, status = 5)
public class FailingAfterEachTest {

    @AfterEach
    void fail() { throw new RuntimeException(); }

    @Test
    void test() { }

}
