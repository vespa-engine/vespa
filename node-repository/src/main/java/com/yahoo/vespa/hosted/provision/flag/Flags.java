// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.flag;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * This class provides feature flags for the node repository. A feature flag can be toggled for the following
 * dimensions:
 *
 * 1) The node repository (entire zone)
 * 2) A specific node
 * 3) A specific application
 *
 * Code which needs to consider feature flags can access them through {@link NodeRepository#flags()}.
 *
 * @author mpolden
 */
public class Flags {

    private final CuratorDatabaseClient db;

    public Flags(CuratorDatabaseClient db) {
        this.db = Objects.requireNonNull(db, "db must be non-null");
    }

    /** Get status for given feature flag */
    public Flag get(FlagId id) {
        return db.readFlag(id).orElseGet(() -> Flag.disabled(id));
    }

    /** Get all known feature flags */
    public List<Flag> list() {
        return Arrays.stream(FlagId.values())
                     .map(this::get)
                     .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    /** Enable feature flag in this node repository */
    public void setEnabled(FlagId flag, boolean enabled) {
        if (enabled) {
            write(flag, Flag::enable);
        } else {
            write(flag, Flag::disable);
        }
    }

    /** Enable feature flag for given application */
    public void setEnabled(FlagId flag, ApplicationId application, boolean enabled) {
        if (enabled) {
            write(flag, (f) -> f.enable(application));
        } else {
            write(flag, (f) -> f.disable(application));
        }
    }

    /** Enable feature flag for given node */
    public void setEnabled(FlagId flag, HostName hostname, boolean enabled) {
        if (enabled) {
            write(flag, (f) -> f.enable(hostname));
        } else {
            write(flag, (f) -> f.disable(hostname));
        }
    }

    private void write(FlagId id, Function<Flag, Flag> updateFunc) {
        try (Lock lock = db.lockFlags()) {
            db.writeFlag(updateFunc.apply(get(id)));
        }
    }

}
