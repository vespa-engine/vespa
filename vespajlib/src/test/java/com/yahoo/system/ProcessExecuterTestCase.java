// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.system;

import com.yahoo.collections.Pair;
import com.yahoo.io.IOUtils;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * @author bratseth
 */
public class ProcessExecuterTestCase {

    @Test
    public void testIt() throws IOException {
        IOUtils.writeFile("tmp123.txt", "hello\nworld", false);
        ProcessExecuter exec = new ProcessExecuter();
        assertEquals(new Pair<>(0, "hello\nworld"), exec.exec("cat tmp123.txt"));
        assertEquals(new Pair<>(0, "hello\nworld"), exec.exec(new String[]{"cat", "tmp123.txt"}));
        new File("tmp123.txt").delete();
    }

    @Test
    public void can_set_process_env_variables() throws IOException {
        var exec = new ProcessExecuter();
        // Environment variables may be multiline, so use `-0` to delimit with null byte instead of newline.
        var ret = exec.exec("env -0", Map.of(
                "VESPA_INTERNAL_TEST_FOO", "zoid",
                "VESPA_INTERNAL_TEST_BAR", "berg"));
        assertEquals(0, ret.getFirst().intValue());
        var envMap = parseEnvKVs(ret.getSecond());
        assertEquals("zoid", envMap.get("VESPA_INTERNAL_TEST_FOO"));
        assertEquals("berg", envMap.get("VESPA_INTERNAL_TEST_BAR"));
    }

    @Test
    public void internal_vespa_log_target_can_not_be_overridden() throws IOException {
        var exec = new ProcessExecuter(false); // Only override log target
        var ret = exec.exec("env -0", Map.of(
                "VESPA_LOG_TARGET", "old_fax_machine",
                "VESPA_LOG_CONTROL_FILE", "grocery_shopping_list.txt",
                "VESPA_SERVICE_NAME", "notepad.exe"));
        assertEquals(0, ret.getFirst().intValue());
        var envMap = parseEnvKVs(ret.getSecond());
        // VESPA_LOG_TARGET shall not be allowed through, but the others will be.
        assertFalse(envMap.containsKey("VESPA_LOG_TARGET"));
        assertEquals("grocery_shopping_list.txt", envMap.get("VESPA_LOG_CONTROL_FILE"));
        assertEquals("notepad.exe", envMap.get("VESPA_SERVICE_NAME"));
    }

    @Test
    public void internal_vespa_log_and_service_env_variables_can_not_be_overridden() throws IOException {
        var exec = new ProcessExecuter(true); // Also override log control/service name
        // Use alternate (arguably better) explicit argument passing
        var ret = exec.exec(new String[]{"env", "-0"}, Map.of(
                "VESPA_LOG_TARGET", "old_fax_machine",
                "VESPA_LOG_CONTROL_FILE", "grocery_shopping_list.txt",
                "VESPA_SERVICE_NAME", "notepad.exe"));
        assertEquals(0, ret.getFirst().intValue());
        // We expect VESPA_LOG_TARGET and VESPA_LOG_CONTROL_FILE to not be present, and
        // for VESPA_SERVICE_NAME to equal `exec-<foo>` where <foo> is the invoked binary.
        var envMap = parseEnvKVs(ret.getSecond());
        assertFalse(envMap.containsKey("VESPA_LOG_TARGET"));
        assertFalse(envMap.containsKey("VESPA_LOG_CONTROL_FILE"));
        assertEquals("exec-env", envMap.get("VESPA_SERVICE_NAME"));
    }

    private static Map<String, String> parseEnvKVs(String str) {
        return Arrays.stream(str.split("\0")).map(line -> {
            String[] parts = line.split("=");
            if (parts.length == 1) { // empty env var value?
                return Map.entry(parts[0], "");
            }
            return Map.entry(parts[0], parts[1]);
        }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

}
