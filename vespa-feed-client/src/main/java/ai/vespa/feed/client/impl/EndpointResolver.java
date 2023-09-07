package ai.vespa.feed.client.impl;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.eclipse.jetty.util.Promise.Completable;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.thread.Scheduler;

public class EndpointResolver {

    private final Scheduler scheduler;

    private final SocketAddressResolver resolver;

    public EndpointResolver(Scheduler scheduler, SocketAddressResolver resolver) {
        this.scheduler = scheduler;
        this.resolver = resolver;
    }

    public void resolveRepeatedly(List<URI> uris, Consumer<Set<URI>> callback, long delay, TimeUnit units) {
        scheduler.schedule(() -> {
            resolveSync(uris, callback);
            resolveRepeatedly(uris, callback, delay, units);
        }, delay, units);
    }

    public void resolveSync(List<URI> uris, Consumer<Set<URI>> callback) {
        Set<URI> addresses = uris.stream()
                .map(this::resolveSingle)
                .map(CompletableFuture::join) // wait and join the futures
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        callback.accept(addresses);
    }

    private CompletableFuture<List<URI>> resolveSingle(URI uri) {
        Completable<List<InetSocketAddress>> result = new Completable<>();
        resolver.resolve(uri.getHost(), uri.getPort(), result);
        return result.thenApply(addresses -> addressesToUris(uri, addresses))
                .exceptionally(t -> List.of()); // ignore exceptions, defer to the consumer
    }

    private List<URI> addressesToUris(URI uri, List<InetSocketAddress> addresses) {
        return addresses.stream()
                .map(address -> cloneUriWithAddress(uri, address))
                .collect(Collectors.toList());
    }

    private URI cloneUriWithAddress(URI uri, InetSocketAddress address) {
        try {
            return new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    address.getAddress().getHostAddress(),
                    uri.getPort(),
                    uri.getPath(),
                    uri.getQuery(),
                    uri.getFragment());
        } catch (URISyntaxException e) {
            // cloning an existing URI, this shouldn't happen
            throw new RuntimeException(e);
        }
    }
}
