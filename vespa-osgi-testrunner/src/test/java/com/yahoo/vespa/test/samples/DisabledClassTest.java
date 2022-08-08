package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("because")
@Expect(skipped = 2, status = 2)
public class DisabledClassTest {

    @Test
    void test() { }

    @Test
    void fest() { }

}
