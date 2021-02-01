// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.state;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;


/**
 * @author olaa
 */
public class CoredumpGathererTest {

    @Test
    public void finds_one_coredump() throws JSONException {
    JSONObject packet = CoredumpGatherer.gatherCoredumpMetrics(new MockFileWrapper());

    assertEquals("system-coredumps-processing", packet.getString("application"));
    assertEquals(1, packet.getInt("status_code"));
    assertEquals("Found 1 coredump(s)", packet.getString("status_msg"));

    }

    static class MockFileWrapper extends FileWrapper {


        @Override
        Stream<Path> walkTree(Path path)  {
            return Stream.of(Path.of("dummy-path"));
        }

        @Override
        boolean isRegularFile(Path path) {
            return true;
        }
    }

}
