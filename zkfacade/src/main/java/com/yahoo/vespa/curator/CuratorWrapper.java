package com.yahoo.vespa.curator;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
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

    @Inject
    public CuratorWrapper(Curator curator) {
        this.curator = curator;
    }

    @Override
    public AutoCloseable lock(Path path, Duration timeout) {
        // TODO jonmv: clear up
        Lock current, old = curator.lock(path, timeout);
        try { current = curator.lock(userRoot.append(path), timeout); }
        catch (Throwable t) { old.close(); throw t; }
        return () -> { try(old) { current.close(); } };
    }

}
