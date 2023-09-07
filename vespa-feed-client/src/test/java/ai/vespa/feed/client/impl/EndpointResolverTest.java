package ai.vespa.feed.client.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.jetty.util.Promise;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.util.thread.Scheduler;
import org.junit.jupiter.api.Test;

public class EndpointResolverTest {
    private final URI HOST1 = URI.create("http://host1:8080");
    private final URI HOST2 = URI.create("http://host2:8080");
    private final URI UNKNOWN = URI.create("http://unknown:8080");

    private class MockScheduler extends AbstractLifeCycle implements Scheduler {
        Runnable task;
        long delay;
        TimeUnit units;
        int count = 0;

        @Override
        public Task schedule(Runnable task, long delay, TimeUnit units) {
            this.task = task;
            this.delay = delay;
            this.units = units;
            count++;
            return () -> false;
        }
    }

    private class MockResolver implements SocketAddressResolver {
        @Override
        public void resolve(String host, int port, Promise<List<InetSocketAddress>> promise) {
            if (host.equals(HOST1.getHost())) {
                List<InetSocketAddress> addresses = List.of(getAddress(new byte[]{1, 1, 1, 1}, 8080));
                promise.succeeded(addresses);
            } else if (host.equals(HOST2.getHost())) {
                List<InetSocketAddress> addresses = List.of(
                    getAddress(new byte[]{1, 1, 1, 1}, 8080),
                    getAddress(new byte[]{1, 1, 1, 2}, 8080));
                promise.succeeded(addresses);
            } else if (host.equals(UNKNOWN.getHost())) {
                promise.failed(new UnknownHostException());
            } else {
                promise.succeeded(List.of());
            }
        }
    }

    @Test
    void testResolve() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        EndpointResolver resolver = new EndpointResolver(new MockScheduler(), new MockResolver());

        // test empty uri list
        resolver.resolveSync(List.of(), uris -> {
            assertTrue(uris.isEmpty());
            callbackCalled.set(true);
        });
        assertTrue(callbackCalled.get());

        // test UnknownHostException
        callbackCalled.set(false);
        resolver.resolveSync(List.of(UNKNOWN), uris -> {
            assertTrue(uris.isEmpty());
            callbackCalled.set(true);
        });
        assertTrue(callbackCalled.get());

        // test single uri and single result
        callbackCalled.set(false);
        resolver.resolveSync(List.of(HOST1), uris -> {
            assertEquals(Set.of(URI.create("http://1.1.1.1:8080")), uris);
            callbackCalled.set(true);
        });
        assertTrue(callbackCalled.get());

        // test multiple uris and multiple results
        callbackCalled.set(false);
        resolver.resolveSync(List.of(HOST1, HOST2, UNKNOWN), uris -> {
            assertEquals(Set.of(URI.create("http://1.1.1.1:8080"), URI.create("http://1.1.1.2:8080")), uris);
            callbackCalled.set(true);
        });
        assertTrue(callbackCalled.get());
    }

    @Test
    void testResolveRepeatedly() {
        AtomicBoolean callbackCalled = new AtomicBoolean(false);
        MockScheduler scheduler = new MockScheduler();
        EndpointResolver resolver = new EndpointResolver(scheduler, new MockResolver());

        resolver.resolveRepeatedly(List.of(), uris -> {
            assertTrue(uris.isEmpty());
            callbackCalled.set(true);
        }, 10, TimeUnit.SECONDS);

        assertFalse(callbackCalled.get()); // not called yet
        assertEquals(10, scheduler.delay);
        assertEquals(TimeUnit.SECONDS, scheduler.units);
        assertEquals(1, scheduler.count);

        scheduler.task.run();

        assertTrue(callbackCalled.get());
        assertEquals(2, scheduler.count); // ensure it gets re-scheduled
    }

    private InetSocketAddress getAddress(byte[] bytes, int port) {
        try {
            return new InetSocketAddress(InetAddress.getByAddress(bytes), port);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }
}
