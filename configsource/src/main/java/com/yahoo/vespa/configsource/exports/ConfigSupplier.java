// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.exports;

import java.util.Optional;

/**
 * A ConfigSupplier is a {@link ConfigSource} specific object for retrieving a config snapshot.
 *
 * @author hakon
 */
public interface ConfigSupplier<T> {
    /**
     * Returns a snapshot of the config.
     *
     * <p>Empty should be returned instead of a default config in case the {@link ConfigSource}
     * does not have that config set, since upstream <em>reducers</em> may need to differentiate
     * between unset/unknown config and a config that happens to be equal to the default config,
     * see discussion in {@link ConfigSource}.
     *
     * The snapshot may be old, either for performance reasons or to overcome transient errors.
     *
     * But it is VERY important to never return a snapshot that is WRONG. For instance, DO NOT
     * return a default value if retrieving the correct config value fails with an error.
     * Production relies on correct settings of the config, so even transiently wrong snapshots
     * can have DIRE consequences.
     *
     * It's implementation defined whether errors are handled internally, or leak out
     * as exceptions. However the end-result must be according to above rule.
     */
    Optional<T> getSnapshot();
}
