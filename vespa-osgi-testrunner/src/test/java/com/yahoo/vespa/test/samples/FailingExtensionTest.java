package com.yahoo.vespa.test.samples;

import com.yahoo.vespa.testrunner.Expect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.Extension;

import static java.util.Objects.requireNonNull;

@Expect(error = 1, status = 5)
public class FailingExtensionTest {

    @Test
    @ExtendWith(FailingExtension.class)
    void test() { }

    static class FailingExtension implements Extension {

        { if (true) throw new NullPointerException(); }

    }

}
