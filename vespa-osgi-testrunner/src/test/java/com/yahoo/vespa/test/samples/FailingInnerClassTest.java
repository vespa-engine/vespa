package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

@Expect(successful = 1, failed = 1, status = 4)
public class FailingInnerClassTest {

    @Nested
    class Failing {

        @Test
        void test() { fail(); }

    }

    @Nested
    class Succeeding {

        @Test
        void test() { }

    }

}
