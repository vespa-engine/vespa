package com.yahoo.vespa.test.samples;

import ai.vespa.hosted.cd.InconclusiveTestException;
import ai.vespa.hosted.cd.ProductionTest;
import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestReporter;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.INFO;
import static java.util.logging.Level.WARNING;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

@ProductionTest
@Expect(successful = 3, skipped = 2, aborted = 1, inconclusive = 1, failed = 2, error = 1, status = 5)
@TestMethodOrder(MethodOrderer.DisplayName.class)
@TestClassOrder(ClassOrderer.DisplayName.class)
public class SampleTest {

    static Handler consoleHandler = null;

    @BeforeAll
    static void setupLogging() {
        Handler[] handlers = Logger.getLogger("").getHandlers();
        for (Handler handler : handlers)
            if (handler instanceof ConsoleHandler)
                consoleHandler = handler;
        Logger.getLogger("").removeHandler(consoleHandler);
    }

    @AfterAll
    static void restoreLogging() {
        Logger.getLogger("").addHandler(consoleHandler);
    }

    private static final Logger log = Logger.getLogger(SampleTest.class.getName());

    @BeforeEach
    void spam() {
        System.err.println("spam");
    }

    @Test
    @Disabled("disabled for test purposes")
    void ignored() { }

    @Test
    void aborted() {
        Assumptions.assumeTrue(false, "thou shalt not pass!");
    }

    @Test
    void successful() {
        log.log(new Level("html", INFO.intValue()) { }, "<body />");
        log.log(INFO, "Very informative: \"\\n\": \n");
        log.log(WARNING, "Oh no", new IllegalArgumentException("error", new RuntimeException("wrapped")));
    }

    @Test
    void failing() {
        log.log(INFO, "I have a bad feeling about this");
        Assertions.assertEquals("foo", "bar", "baz");
    }

    @Test
    void error() {
        log.log(FINE, "What could possibly go wrong this time?");
        throw new NoClassDefFoundError();
    }

    @Test
    void inconclusive(TestReporter reporter) {
        reporter.publishEntry("I'm here with Erwin today; Erwin, what can you tell us about your cat?");
        throw new InconclusiveTestException("the cat is both dead _and_ alive");
    }

    @Nested
    class Inner {

        @Test
        void first() { }

        @TestFactory
        Stream<DynamicTest> others() {
            return Stream.of(dynamicTest("second", () -> System.out.println("Catch me if you can!")),
                             dynamicTest("third", () -> Assertions.fail("no charm")));
        }

    }

    @Nested
    @Disabled
    class Skipped {

        @Test
        void disabled() { }

    }

}
