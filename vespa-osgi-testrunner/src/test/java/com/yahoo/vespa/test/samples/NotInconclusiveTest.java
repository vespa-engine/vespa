package com.yahoo.vespa.test.samples;

import ai.vespa.hosted.cd.InconclusiveTestException;
import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Test;

@Expect(error = 1, status = 5)
public class NotInconclusiveTest {

    @Test
    void test() { throw new InconclusiveTestException("soon"); }

}
