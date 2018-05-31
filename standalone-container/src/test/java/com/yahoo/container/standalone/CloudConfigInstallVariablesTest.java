// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.standalone;

import com.yahoo.vespa.model.container.configserver.option.CloudConfigOptions;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.yahoo.container.standalone.CloudConfigInstallVariables.toConfigModelsPluginDir;
import static com.yahoo.container.standalone.CloudConfigInstallVariables.toConfigServer;
import static com.yahoo.container.standalone.CloudConfigInstallVariables.toConfigServers;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 * @author Tony Vaagenes
 */
public class CloudConfigInstallVariablesTest {

    @Test
    public void test_configserver_parsing() {
        CloudConfigOptions.ConfigServer[] parsed = toConfigServers("myhost.mydomain.com");
        assertThat(parsed.length, is(1));
    }

    @Test
    public void port_can_be_configured() {
        CloudConfigOptions.ConfigServer[] parsed = toConfigServers("myhost:123");
        int port = parsed[0].port.get();
        assertThat(port, is(123));
    }

    @Test
    public void multiple_spaces_are_supported() {
        CloudConfigOptions.ConfigServer[] parsed = toConfigServers("test1     test2");
        assertThat(parsed.length, is(2));

        List<String> hostNames = Arrays.stream(parsed).map(cs -> cs.hostName).collect(Collectors.toList());
        assertThat(hostNames, contains("test1", "test2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void missing_port_gives_exception() {
        toConfigServer("myhost:");
    }

    @Test(expected = IllegalArgumentException.class)
    public void non_numeric_port_gives_exception() {
        toConfigServer("myhost:non-numeric");
    }

    @Test
    public void string_arrays_are_split_on_spaces() {
        String[] parsed = toConfigModelsPluginDir("/home/vespa/foo /home/vespa/bar ");
        assertThat(parsed.length, is(2));
    }

    @Test
    public void string_arrays_are_split_on_comma() {
        String[] parsed = toConfigModelsPluginDir("/home/vespa/foo,/home/vespa/bar,");
        assertThat(parsed.length, is(2));
    }
}
