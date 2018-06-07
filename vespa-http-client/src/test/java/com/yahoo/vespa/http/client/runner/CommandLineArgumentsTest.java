// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.runner;

import com.yahoo.vespa.http.client.config.Cluster;
import com.yahoo.vespa.http.client.config.FeedParams;
import com.yahoo.vespa.http.client.config.SessionParams;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.*;

public class CommandLineArgumentsTest {

    private String[] asArray() {
        String[] array = new String[args.size()];
        args.toArray(array);
        return array;
    }

    private void add(String key, String value) {
        args.add("--" + key);
        args.add(value);
    }

    private void addMinimum() {
        add("host", "hostValue");
    }

    private ArrayList<String> args = new ArrayList<>();

    @Test
    public void testRequiredFlags() {
        assertThat(CommandLineArguments.build(asArray()), is(nullValue()));
        add("file", "fileValue");
        assertThat(CommandLineArguments.build(asArray()), is(nullValue()));
        args.clear();
        addMinimum();
        assertThat(CommandLineArguments.build(asArray()), is(not(nullValue())));
    }

    @Test
    public void testStreaming() {
        add("host", "hostValue");
        add("file", null);  // Not yet implemented support for streaming
        assertThat(CommandLineArguments.build(asArray()), is(nullValue()));
    }

    @Test
    public void testBrokenFlags() {
        addMinimum();
        args.add("FOO");
        assertThat(CommandLineArguments.build(asArray()), is(nullValue()));
    }

    @Test
    public void testBadPriority() {
        addMinimum();
        add("priority", "non existing");
        assertThat(CommandLineArguments.build(asArray()), is(nullValue()));
    }

    @Test
    public void testOkPriority() {
        addMinimum();
        add("priority", "HIGHEST");
        assertThat(CommandLineArguments.build(asArray()).createSessionParams(false).getFeedParams().getPriority(),
                is("HIGHEST"));
    }

    @Test
    public void testDefaults() {
        addMinimum();
        CommandLineArguments arguments = CommandLineArguments.build(asArray());
        SessionParams params = arguments.createSessionParams(false /* use json */);
        assertThat(params.getClientQueueSize(), is(10000));
        assertThat(params.getThrottlerMinSize(), is(0));
        assertThat(params.getClusters().size(), is(1));
        assertThat(params.getClusters().get(0).getEndpoints().size(), is(1));
        assertThat(params.getClusters().get(0).getEndpoints().get(0).getHostname(), is("hostValue"));
        assertThat(params.getClusters().get(0).getEndpoints().get(0).getPort(), is(4080));
        assertThat(params.getClusters().get(0).getEndpoints().get(0).isUseSsl(), is(false));
        assertThat(params.getConnectionParams().getUseCompression(), is(false));
        assertThat(params.getConnectionParams().getNumPersistentConnectionsPerEndpoint(), is(16));
        assertThat(params.getFeedParams().getRoute(), is("default"));
        assertThat(params.getFeedParams().getDataFormat(), is(FeedParams.DataFormat.XML_UTF8));
        assertThat(params.getFeedParams().getLocalQueueTimeOut(), is(180000L));
        assertThat(params.getFeedParams().getMaxInFlightRequests(), is(10000));
        assertThat(params.getFeedParams().getClientTimeout(TimeUnit.MILLISECONDS), is(180000L));
    }

    @Test
    public void testAllImplementedFlags() {
        add("file", "fileValue.json");
        add("route", "routeValue");
        add("host", "hostValue");
        add("port", "1234");
        add("timeout", "2345");
        args.add("--useCompression");
        args.add("--useDynamicThrottling");
        add("maxpending", "3456");
        add("debugport", "7890");
        args.add("--verbose");
        args.add("--useTls");
        add("header", "Header-Name: Header-Value");
        CommandLineArguments arguments = CommandLineArguments.build(asArray());
        SessionParams params = arguments.createSessionParams(true /* use json */);
        assertThat(params.getClientQueueSize(), is(3456));
        assertThat(params.getThrottlerMinSize(), is(10));
        assertThat(params.getClusters().get(0).getEndpoints().get(0).getPort(), is(1234));
        assertThat(params.getClusters().get(0).getEndpoints().get(0).isUseSsl(), is(true));
        assertThat(params.getConnectionParams().getUseCompression(), is(true));
        assertThat(params.getConnectionParams().getHeaders().size(), is(1));
        assertThat(params.getFeedParams().getRoute(), is("routeValue"));
        assertThat(params.getFeedParams().getDataFormat(), is(FeedParams.DataFormat.JSON_UTF8));
        assertThat(params.getFeedParams().getLocalQueueTimeOut(), is(2345000L));
        assertThat(params.getFeedParams().getMaxInFlightRequests(), is(3456));
        assertThat(params.getFeedParams().getClientTimeout(TimeUnit.MILLISECONDS), is(2345000L));
    }

    @Test
    public void testAddingMultipleHttpHeaders() {
        add("host", "hostValue");
        String header1Name = "Header-Name-1";
        String header1Value = "Header-Value";
        add("header", header1Name + ": " + header1Value);
        String header2Name = "Header-Name-2";
        String header2Value = "Another-Header-Value";
        add("header", header2Name + ": " + header2Value);

        CommandLineArguments arguments = CommandLineArguments.build(asArray());
        SessionParams params = arguments.createSessionParams(true /* use json */);

        List<Map.Entry<String, String>> headers = new ArrayList<>(params.getConnectionParams().getHeaders());
        headers.sort(Comparator.comparing(Map.Entry::getKey));

        assertThat(headers.size(), is(2));
        Map.Entry<String, String> actualHeader1 = headers.get(0);
        assertThat(actualHeader1.getKey(), is(header1Name));
        assertThat(actualHeader1.getValue(), is(header1Value));
        Map.Entry<String, String> actualHeader2 = headers.get(1);
        assertThat(actualHeader2.getKey(), is(header2Name));
        assertThat(actualHeader2.getValue(), is(header2Value));
    }

    @Test
    public void testMultiHost() {
        add("file", "fileValue.json");
        add("port", "1234");
        add("host", "hostValue1,hostValue2,      hostValue3");
        CommandLineArguments arguments = CommandLineArguments.build(asArray());
        SessionParams params = arguments.createSessionParams(true /* use json */);
        assertThat(params.getClusters().size(), is(3));
        final Set<String> hosts = new HashSet<>();
        for (Cluster cluster : params.getClusters()) {
            assertThat(cluster.getEndpoints().size(), is(1));
            hosts.add(cluster.getEndpoints().get(0).getHostname());
            assertThat(cluster.getEndpoints().get(0).getPort(), is(1234));
        }
        assertThat(hosts, hasItem("hostValue1"));
        assertThat(hosts, hasItem("hostValue2"));
        assertThat(hosts, hasItem("hostValue3"));
    }
}
