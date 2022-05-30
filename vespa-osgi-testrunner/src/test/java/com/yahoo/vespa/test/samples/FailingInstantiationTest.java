package com.yahoo.vespa.test.samples;

import ai.vespa.hosted.cd.SystemTest;
import org.junit.jupiter.api.Test;

@SystemTest
public class FailingInstantiationTest {

    final int i = Integer.parseInt("");

    @Test
    void test() { }

}
