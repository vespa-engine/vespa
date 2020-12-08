package com.yahoo.vespa.flags;// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

import com.yahoo.vespa.flags.custom.HostResources;
import com.yahoo.vespa.flags.custom.SharedHost;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.vespa.flags.FlagsTest.testGeneric;

/**
 * @author bjorncs
 */
class PermanentFlagsTest {
    @Test
    public void testSharedHostFlag() {
        SharedHost sharedHost = new SharedHost(List.of(new HostResources(
                4.0, 16.0, 50.0, 0.3,
                "fast", "local",
                10)),
                null);
        testGeneric(PermanentFlags.SHARED_HOST, sharedHost);
    }

}