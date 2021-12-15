// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class ApplicationCuratorDatabaseTest {

    @Test
    public void testReindexingStatusSerialization() {
        ApplicationId id = ApplicationId.defaultId();
        ApplicationCuratorDatabase db = new ApplicationCuratorDatabase(id.tenant(), new MockCurator());

        assertEquals(Optional.empty(), db.readReindexingStatus(id));

        ApplicationReindexing reindexing = ApplicationReindexing.empty()
                                                                .withPending("one", "a", 10).withReady("two", "b", Instant.ofEpochMilli(2), 0.2)
                                                                .withPending("two", "b", 20).withReady("one", "a", Instant.ofEpochMilli(1), 0.2).withReady("two", "c", Instant.ofEpochMilli(3), 0.2)
                                                                .enabled(false);

        db.writeReindexingStatus(id, reindexing);
        assertEquals(reindexing, db.readReindexingStatus(id).orElseThrow());
    }

}
