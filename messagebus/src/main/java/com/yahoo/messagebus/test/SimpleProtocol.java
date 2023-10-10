// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.test;

import com.yahoo.component.Version;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.Routable;
import com.yahoo.messagebus.routing.RoutingPolicy;
import com.yahoo.text.Utf8;
import com.yahoo.text.Utf8String;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleProtocol implements Protocol {

    public static final Utf8String NAME = new Utf8String("Simple");
    public static final int MESSAGE = 1;
    public static final int REPLY = 2;
    private final Map<String, PolicyFactory> policies = new HashMap<String, PolicyFactory>();

    @Override
    public String getName() {
        return NAME.toString();
    }

    @Override
    public RoutingPolicy createPolicy(String name, String param) {
        if (policies.containsKey(name)) {
            return policies.get(name).create(param);
        }
        return null;
    }

    @Override
    public Routable decode(Version version, byte[] data) {
        String str = Utf8.toString(data);
        if (str.length() < 1) {
            return null;
        }
        char c = str.charAt(0);
        if (c == 'M') {
            return new SimpleMessage(str.substring(1));
        }
        if (c == 'R') {
            return new SimpleReply(str.substring(1));
        }
        return null;
    }

    @Override
    public byte[] encode(Version version, Routable routable) {
        if (routable.getType() == MESSAGE) {
            return Utf8.toBytes("M" + ((SimpleMessage)routable).getValue());
        } else if (routable.getType() == REPLY) {
            return Utf8.toBytes("R" + ((SimpleReply)routable).getValue());
        } else {
            return null;
        }
    }

    /**
     * Registers a policy factory with this protocol under a given name. Whenever a policy is requested that matches
     * this name, the factory is invoked.
     *
     * @param name    The name of the policy.
     * @param factory The policy factory.
     */
    public void addPolicyFactory(String name, PolicyFactory factory) {
        policies.put(name, factory);
    }

    /**
     * Defines a policy factory interface that tests can use to register arbitrary policies with this protocol.
     */
    public interface PolicyFactory {

        /**
         * Creates a new instance of the routing policy that this factory encapsulates.
         *
         * @param param The param for the policy constructor.
         * @return The routing policy created.
         */
        public RoutingPolicy create(String param);
    }
}
