package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@Expect(skipped = 2, status = 5)
public class WrongBeforeAllTest {

    @BeforeAll
    void wrong() { }

    @Test
    void test() { }

    @Test
    void fest() { }

}
