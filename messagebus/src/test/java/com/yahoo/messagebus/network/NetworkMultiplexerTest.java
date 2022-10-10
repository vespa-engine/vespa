// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network;

import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.routing.RoutingNode;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import com.yahoo.text.Utf8Array;
import com.yahoo.text.Utf8String;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author jonmv
 */
public class NetworkMultiplexerTest {

    @Test
    void testShared() {
        MockNetwork net = new MockNetwork();
        MockOwner owner1 = new MockOwner();
        MockOwner owner2 = new MockOwner();
        NetworkMultiplexer shared = NetworkMultiplexer.shared(net);
        assertEquals(Set.of(shared), net.attached);
        assertEquals(Set.of(), net.registered);
        assertFalse(net.shutDown.get());

        shared.attach(owner1);
        shared.registerSession("s1", owner1, true);
        try {
            shared.registerSession("s1", owner1, true);
            fail("Illegal to register same session multiple times with the same owner");
        }
        catch (IllegalArgumentException expected) {
            assertEquals("Session 's1' with owner 'mock owner' already registered with network multiplexer with owners: [mock owner], sessions: {s1=[mock owner]} and destructible: false",
                         expected.getMessage());
        }
        assertEquals(Set.of("s1"), net.registered);

        shared.attach(owner2);
        shared.registerSession("s2", owner2, true);
        shared.registerSession("s3", owner2, false);
        assertEquals(Set.of("s1", "s2"), net.registered);

        Utf8String name = new Utf8String("protocol");
        Protocol protocol1 = new SimpleProtocol();
        Protocol protocol2 = new SimpleProtocol();
        owner1.protocols.put(name, protocol1);
        assertEquals(protocol1, shared.getProtocol(name));
        owner2.protocols.put(name, protocol2);
        assertEquals(protocol2, shared.getProtocol(name));

        Message message1 = new SimpleMessage("one");
        Message message2 = new SimpleMessage("two");
        Message message3 = new SimpleMessage("three");
        Message message4 = new SimpleMessage("four");
        Message message5 = new SimpleMessage("five");
        shared.deliverMessage(message1, "s1");
        shared.deliverMessage(message2, "s2");

        // New "s1" owner connects, and should have new requests.
        shared.registerSession("s1", owner2, true);
        shared.deliverMessage(message3, "s1");
        shared.deliverMessage(message4, "s3");
        shared.unregisterSession("s1", owner1, true);
        shared.deliverMessage(message5, "s1");
        assertEquals(Map.of("s1", List.of(message1)), owner1.messages);
        assertEquals(Map.of("s2", List.of(message2), "s1", List.of(message3, message5), "s3", List.of(message4)), owner2.messages);

        shared.detach(owner1);
        assertEquals(protocol2, shared.getProtocol(name));

        shared.detach(owner2);
        assertFalse(net.shutDown.get());

        shared.attach(owner2);
        shared.disown();
        assertFalse(net.shutDown.get());

        shared.detach(owner2);
        assertTrue(net.shutDown.get());
    }

    @Test
    void testDedicated() {
        MockNetwork net = new MockNetwork();
        MockOwner owner = new MockOwner();
        NetworkMultiplexer dedicated = NetworkMultiplexer.dedicated(net);
        assertEquals(Set.of(dedicated), net.attached);
        assertEquals(Set.of(), net.registered);
        assertFalse(net.shutDown.get());

        dedicated.attach(owner);
        dedicated.detach(owner);
        assertTrue(net.shutDown.get());
    }

    static class MockOwner implements NetworkOwner {

        final Map<Utf8Array, Protocol> protocols = new HashMap<>();
        final Map<String, List<Message>> messages = new HashMap<>();

        @Override
        public Protocol getProtocol(Utf8Array name) {
            return protocols.get(name);
        }

        @Override
        public void deliverMessage(Message message, String session) {
            messages.computeIfAbsent(session, __ -> new ArrayList<>()).add(message);
        }

        @Override
        public String toString() {
            return "mock owner";
        }

    }

    static class MockNetwork implements Network {

        final Set<NetworkOwner> attached = new HashSet<>();
        final Set<String> registered = new HashSet<>();
        final AtomicBoolean shutDown = new AtomicBoolean();

        @Override
        public boolean waitUntilReady(double seconds) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void attach(NetworkOwner owner) {
            assertTrue(attached.add(owner));
        }

        @Override
        public void registerSession(String session) {
            assertTrue(registered.add(session));
        }

        @Override
        public void unregisterSession(String session) {
            assertTrue(registered.remove(session));
        }

        @Override
        public boolean allocServiceAddress(RoutingNode recipient) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void freeServiceAddress(RoutingNode recipient) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void send(Message msg, List<RoutingNode> recipients) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void sync() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
            assertFalse(shutDown.getAndSet(true));
        }

        @Override
        public String getConnectionSpec() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IMirror getMirror() {
            throw new UnsupportedOperationException();
        }

    }

}
