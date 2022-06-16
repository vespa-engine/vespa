package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.IntStream;
import java.util.stream.Stream;

@Expect(skipped = 1, status = 5)
public class FailingTestFactoryTest {

    @BeforeAll
    static void fail() { throw new RuntimeException(); }

    @TestFactory
    Stream<DynamicTest> tests() {
        return IntStream.range(0, 3).mapToObj(i -> DynamicTest.dynamicTest("test-" + i, () -> { throw new RuntimeException("error"); }));
    }

    @Test
    void fest() { }

}
