// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.yahoo.vespa.model.container.configserver.option.ConfigOptions;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.yahoo.container.standalone.ConfigEnvironmentVariables.toConfigServer;
import static com.yahoo.container.standalone.ConfigEnvironmentVariables.toConfigServers;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Ulf Lilleengen
 * @author Tony Vaagenes
 */
public class ConfigEnvironmentVariablesTest {

    @Test
    public void test_configserver_parsing() {
        ConfigOptions.ConfigServer[] parsed = toConfigServers("myhost.mydomain.com");
        assertEquals(1, parsed.length);
    }

    @Test
    public void port_can_be_configured() {
        ConfigOptions.ConfigServer[] parsed = toConfigServers("myhost:123");
        int port = parsed[0].port.get();
        assertEquals(123, port);
    }

    @Test
    public void multiple_spaces_are_supported() {
        ConfigOptions.ConfigServer[] parsed = toConfigServers("test1     test2");
        assertEquals(2, parsed.length);

        List<String> hostNames = Arrays.stream(parsed).map(cs -> cs.hostName).toList();
        assertTrue(hostNames.containsAll(List.of("test1", "test2")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missing_port_gives_exception() {
        toConfigServer("myhost:");
    }

    @Test(expected = IllegalArgumentException.class)
    public void non_numeric_port_gives_exception() {
        toConfigServer("myhost:non-numeric");
    }

}
