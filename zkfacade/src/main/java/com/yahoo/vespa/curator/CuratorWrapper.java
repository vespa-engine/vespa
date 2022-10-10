package com.yahoo.vespa.curator;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.jdisc.Metric;
import com.yahoo.path.Path;
import com.yahoo.vespa.curator.api.VespaCurator;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.zookeeper.KeeperException.BadVersionException;
import org.apache.zookeeper.data.Stat;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of {@link VespaCurator} which delegates to a {@link Curator}.
 * This prefixes all paths with {@code "/user"}, to ensure separation with system ZK usage.
 *
 * @author jonmv
 */
public class CuratorWrapper extends AbstractComponent implements VespaCurator {

    static final Path userRoot = Path.fromString("user");

    private final Curator curator;
    private final SingletonManager singletons;

    @Inject
    public CuratorWrapper(Curator curator, Metric metric) {
        this(curator, Clock.systemUTC(), Duration.ofSeconds(1), metric);
    }

    CuratorWrapper(Curator curator, Clock clock, Duration tickTimeout, Metric metric) {
        this.curator = curator;
        this.singletons = new SingletonManager(curator, clock, tickTimeout, metric);

        curator.framework().getConnectionStateListenable().addListener((curatorFramework, connectionState) -> {
            if (connectionState == ConnectionState.LOST) singletons.invalidate();
        });
    }

    @Override
    public Optional<Meta> stat(Path path) {
        return curator.getStat(userRoot.append(path)).map(stat -> new Meta(stat.getVersion()));
    }

    @Override
    public Optional<Data> read(Path path) {
        Stat stat = new Stat();
        return curator.getData(userRoot.append(path), stat).map(data -> new Data(new Meta(stat.getVersion()), data));
    }

    @Override
    public Meta write(Path path, byte[] data) {
        return new Meta(curator.set(userRoot.append(path), data).getVersion());
    }

    @Override
    public Optional<Meta> write(Path path, byte[] data, int expectedVersion) {
        try {
            return Optional.of(new Meta(curator.set(userRoot.append(path), data, expectedVersion).getVersion()));
        }
        catch (RuntimeException e) {
            if (e.getCause() instanceof BadVersionException) return Optional.empty();
            throw e;
        }
    }

    @Override
    public void deleteAll(Path path) {
        curator.delete(userRoot.append(path));
    }

    @Override
    public void delete(Path path) {
        curator.delete(userRoot.append(path), false);
    }

    @Override
    public boolean delete(Path path, int expectedVersion) {
        try {
            curator.delete(userRoot.append(path), expectedVersion, false);
            return true;
        }
        catch (RuntimeException e) {
            if (e.getCause() instanceof BadVersionException) return false;
            throw e;
        }
    }

    @Override
    public List<String> list(Path path) {
        return curator.getChildren(userRoot.append(path));
    }

    @Override
    public AutoCloseable lock(Path path, Duration timeout) {
        // TODO jonmv: clear up
        Lock current, old = curator.lock(path, timeout);
        try { current = curator.lock(userRoot.append(path), timeout); }
        catch (Throwable t) { old.close(); throw t; }
        return () -> { try(old) { current.close(); } };
    }

    @Override
    public CompletableFuture<?> registerSingleton(String singletonId, SingletonWorker singleton) {
        return singletons.register(singletonId, singleton);
    }

    @Override
    public CompletableFuture<?> unregisterSingleton(SingletonWorker singleton) {
        return singletons.unregister(singleton);
    }

    @Override
    public boolean isActive(String singletonId) {
        return singletons.isActive(singletonId);
    }

    @Override
    public void deconstruct() {
        singletons.close();
    }

}
