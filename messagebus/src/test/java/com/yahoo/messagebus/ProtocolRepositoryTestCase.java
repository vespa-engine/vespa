// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.messagebus.routing.RoutingContext;
import com.yahoo.messagebus.routing.RoutingPolicy;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class ProtocolRepositoryTestCase {

    @Test
    void requireThatPolicyCanBeNull() {
        ProtocolRepository repo = new ProtocolRepository();
        SimpleProtocol protocol = new SimpleProtocol();
        repo.putProtocol(protocol);
        assertNull(repo.getRoutingPolicy(SimpleProtocol.NAME, "Custom", null));
    }

    @Test
    void requireThatPolicyCanBeCreated() {
        ProtocolRepository repo = new ProtocolRepository();
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new MyFactory());
        repo.putProtocol(protocol);
        assertNotNull(repo.getRoutingPolicy(SimpleProtocol.NAME, "Custom", null));
    }

    @Test
    void requireThatPolicyIsCached() {
        ProtocolRepository repo = new ProtocolRepository();
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new MyFactory());
        repo.putProtocol(protocol);

        RoutingPolicy prev = repo.getRoutingPolicy(SimpleProtocol.NAME, "Custom", null);
        assertNotNull(prev);

        RoutingPolicy next = repo.getRoutingPolicy(SimpleProtocol.NAME, "Custom", null);
        assertNotNull(next);
        assertSame(prev, next);
    }

    @Test
    void requireThatPolicyParamIsPartOfCacheKey() {
        ProtocolRepository repo = new ProtocolRepository();
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new MyFactory());
        repo.putProtocol(protocol);

        RoutingPolicy prev = repo.getRoutingPolicy(SimpleProtocol.NAME, "Custom", "foo");
        assertNotNull(prev);

        RoutingPolicy next = repo.getRoutingPolicy(SimpleProtocol.NAME, "Custom", "bar");
        assertNotNull(next);
        assertNotSame(prev, next);
    }

    @Test
    void requireThatCreatePolicyExceptionIsCaught() {
        ProtocolRepository repo = new ProtocolRepository();
        SimpleProtocol protocol = new SimpleProtocol();
        protocol.addPolicyFactory("Custom", new SimpleProtocol.PolicyFactory() {

            @Override
            public RoutingPolicy create(String param) {
                throw new RuntimeException();
            }
        });
        repo.putProtocol(protocol);
        assertNull(repo.getRoutingPolicy(SimpleProtocol.NAME, "Custom", null));
    }

    private static class MyFactory implements SimpleProtocol.PolicyFactory {

        @Override
        public RoutingPolicy create(String param) {
            return new MyPolicy();
        }
    }

    private static class MyPolicy implements RoutingPolicy {

        @Override
        public void select(RoutingContext context) {

        }

        @Override
        public void merge(RoutingContext context) {

        }

        @Override
        public void destroy() {

        }
    }
}
