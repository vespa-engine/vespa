package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Expect(skipped = 1, status = 2)
public class DisabledTest {

    @Test
    @Disabled("because")
    void test() { }

}
