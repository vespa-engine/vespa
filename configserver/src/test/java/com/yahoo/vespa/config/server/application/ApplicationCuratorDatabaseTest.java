// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Assert;
import org.junit.Test;

import java.time.Instant;

import static org.junit.Assert.assertEquals;

/**
 * @author jonmv
 */
public class ApplicationCuratorDatabaseTest {

    @Test
    public void testReindexingStatusSerialization() {
        ApplicationId id = ApplicationId.defaultId();
        ApplicationCuratorDatabase db = new ApplicationCuratorDatabase(new MockCurator());

        assertEquals(ReindexingStatus.empty(), db.readReindexingStatus(id));

        ReindexingStatus status = ReindexingStatus.empty()
                                                  .withPending("pending1", 1)
                                                  .withPending("pending2", 2)
                                                  .withReady("ready1", Instant.ofEpochMilli(123))
                                                  .withReady("ready2", Instant.ofEpochMilli(321));
        db.writeReindexingStatus(id, status);
        assertEquals(status, db.readReindexingStatus(id));
    }

}
