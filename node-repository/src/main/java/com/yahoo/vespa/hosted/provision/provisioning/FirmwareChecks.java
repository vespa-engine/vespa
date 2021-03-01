// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.lang.CachedSupplier;
import com.yahoo.vespa.hosted.provision.persistence.CuratorDatabaseClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Keeps cached data about when to do a firmware check on the hosts managed by a node repository.
 *
 * The data kept here is managed through POST and DELETE to <code>/nodes/v2/upgrade/firmware</code>
 * <p>
 * Reads and writes are not locked, as writes do not depend on prior state.
 * <p>
 * Local cache expires periodically, and on writes from this host, for testing.
 *
 * @author jonmv
 */
public class FirmwareChecks {

    private static final Duration cacheExpiry = Duration.ofMinutes(1);

    private final CuratorDatabaseClient database;
    private final Clock clock;
    private final CachedSupplier<Optional<Instant>> checkAfter;

    public FirmwareChecks(CuratorDatabaseClient database, Clock clock) {
        this.database = database;
        this.clock = clock;
        this.checkAfter = new CachedSupplier<>(database::readFirmwareCheck, cacheExpiry);
    }

    /** Returns the instant after which a firmware check is required, or empty if none currently are. */
    public Optional<Instant> requiredAfter() {
        return checkAfter.get();
    }

    /** Requests a firmware check for all hosts managed by this node repository. */
    public void request() {
        database.writeFirmwareCheck(Optional.of(clock.instant()));
        checkAfter.invalidate();
    }

    /** Clears any outstanding firmware checks for this node repository. */
    public void cancel() {
        database.writeFirmwareCheck(Optional.empty());
        checkAfter.invalidate();
    }

}
