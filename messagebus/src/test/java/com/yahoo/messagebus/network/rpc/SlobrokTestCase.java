// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;


import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.Spec;
import com.yahoo.jrt.slobrok.api.Mirror;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.network.Identity;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import static org.junit.Assert.assertTrue;


/**
 * @author havardpe
 */
public class SlobrokTestCase {

    private static class Res {
        private List<Mirror.Entry> lst = new ArrayList<>();
        public Res add(String fullName, String spec) {
            lst.add(new Mirror.Entry(fullName, spec));
            return this;
        }
        public Mirror.Entry[] toArray() {
            return lst.toArray(new Mirror.Entry[lst.size()]);
        }
    }

    Slobrok    slobrok;
    RPCNetwork net1;
    RPCNetwork net2;
    RPCNetwork net3;
    int        port1;
    int        port2;
    int        port3;

    void check(RPCNetwork net, String pattern, Mirror.Entry[] expect) {
        Comparator<Mirror.Entry> cmp = new Comparator<Mirror.Entry>() {
            public int compare(Mirror.Entry a, Mirror.Entry b) {
                return a.compareTo(b);
            }
        };
        Arrays.sort(expect, cmp);
        Mirror.Entry[] actual = null;
        for (int i = 0; i < 1000; i++) {
            actual = net.getMirror().lookup(pattern);
            Arrays.sort(actual, cmp);
            if (Arrays.equals(actual, expect)) {
                System.out.printf("lookup successful for pattern: %s\n", pattern);
                return;
            }
            try { Thread.sleep(10); } catch (InterruptedException e) {
                //
            }
        }
        System.out.printf("lookup failed for pattern: %s\n", pattern);
        System.out.printf("actual values:\n");
        if (actual == null || actual.length == 0) {
            System.out.printf("  { EMPTY }\n");
        } else {
            for (Mirror.Entry entry : actual) {
                System.out.printf("  { %s, %s }\n", entry.getName(), entry.getSpec());
            }
        }
        System.out.printf("expected values:\n");
        if (expect.length == 0) {
            System.out.printf("  { EMPTY }\n");
        } else {
            for (Mirror.Entry entry : expect) {
                System.out.printf("  { %s, %s }\n", entry.getName(),  entry.getSpec());
            }
        }
        assertTrue(false);
    }

    @Before
    public void setUp() throws ListenFailedException {
        slobrok = new Slobrok();
        String slobrokCfgId = "raw:slobrok[1]\nslobrok[0].connectionspec \"" + new Spec("localhost", slobrok.port()).toString() + "\"\n";
        net1 = new RPCNetwork(new RPCNetworkParams().setIdentity(new Identity("net/a")).setSlobrokConfigId(slobrokCfgId));
        net2 = new RPCNetwork(new RPCNetworkParams().setIdentity(new Identity("net/b")).setSlobrokConfigId(slobrokCfgId));
        net3 = new RPCNetwork(new RPCNetworkParams().setIdentity(new Identity("net/c")).setSlobrokConfigId(slobrokCfgId));
        port1 = net1.getPort();
        port2 = net2.getPort();
        port3 = net3.getPort();
    }

    @After
    public void tearDown() {
        net3.shutdown();
        net2.shutdown();
        net1.shutdown();
        slobrok.stop();
    }

    @Test
    public void testSlobrok() {
        net1.registerSession("foo");
        net2.registerSession("foo");
        net2.registerSession("bar");
        net3.registerSession("foo");
        net3.registerSession("bar");
        net3.registerSession("baz");

        check(net1, "*/*/*", new Res()
              .add("net/a/foo", net1.getConnectionSpec())
              .add("net/b/foo", net2.getConnectionSpec())
              .add("net/b/bar", net2.getConnectionSpec())
              .add("net/c/foo", net3.getConnectionSpec())
              .add("net/c/bar", net3.getConnectionSpec())
              .add("net/c/baz", net3.getConnectionSpec()).toArray());
        check(net2, "*/*/*", new Res()
              .add("net/a/foo", net1.getConnectionSpec())
              .add("net/b/foo", net2.getConnectionSpec())
              .add("net/b/bar", net2.getConnectionSpec())
              .add("net/c/foo", net3.getConnectionSpec())
              .add("net/c/bar", net3.getConnectionSpec())
              .add("net/c/baz", net3.getConnectionSpec()).toArray());
        check(net3, "*/*/*", new Res()
              .add("net/a/foo", net1.getConnectionSpec())
              .add("net/b/foo", net2.getConnectionSpec())
              .add("net/b/bar", net2.getConnectionSpec())
              .add("net/c/foo", net3.getConnectionSpec())
              .add("net/c/bar", net3.getConnectionSpec())
              .add("net/c/baz", net3.getConnectionSpec()).toArray());

        net2.unregisterSession("bar");
        net3.unregisterSession("bar");
        net3.unregisterSession("baz");

        check(net1, "*/*/*", new Res()
              .add("net/a/foo", net1.getConnectionSpec())
              .add("net/b/foo", net2.getConnectionSpec())
              .add("net/c/foo", net3.getConnectionSpec()).toArray());
        check(net2, "*/*/*", new Res()
              .add("net/a/foo", net1.getConnectionSpec())
              .add("net/b/foo", net2.getConnectionSpec())
              .add("net/c/foo", net3.getConnectionSpec()).toArray());
        check(net3, "*/*/*", new Res()
              .add("net/a/foo", net1.getConnectionSpec())
              .add("net/b/foo", net2.getConnectionSpec())
              .add("net/c/foo", net3.getConnectionSpec()).toArray());
    }

}
