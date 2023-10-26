// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.test;

import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.SimpleConfigProducer;

/**
 * @author Tony Vaagenes
 */
public class MockHosts {

    private final MockRoot root = new MockRoot();
    private final SimpleConfigProducer<Host> hosts = new SimpleConfigProducer<>(root, "hosts");

    public final Host host1 = new Host(hosts, "host-01.example.yahoo.com");
    public final Host host2 = new Host(hosts, "host-02.example.yahoo.com");
    public final Host host3 = new Host(hosts, "host-03.example.yahoo.com");

}
