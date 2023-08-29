// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.application;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.mock.MockCurator;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import org.junit.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

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
                                                                .withPending("one", "a", 10)
                                                                .withReady("two", "b", Instant.ofEpochMilli(2), 0.2, "test reindexing")
                                                                .withPending("two", "b", 20)
                                                                .withReady("one", "a", Instant.ofEpochMilli(1), 0.2, "test reindexing")
                                                                .withReady("two", "c", Instant.ofEpochMilli(3), 0.2, "test reindexing")
                                                                .enabled(false);

        db.writeReindexingStatus(id, reindexing);
        assertEquals(reindexing, db.readReindexingStatus(id).orElseThrow());
    }

    @Test
    public void testReadingAndWritingApplicationData() {
        ApplicationId id = ApplicationId.defaultId();
        MockCurator curator = new MockCurator();
        ApplicationCuratorDatabase db = new ApplicationCuratorDatabase(id.tenant(), curator);

        assertEquals(Optional.empty(), db.applicationData(id));

        db.createApplication(id, false);
        assertEquals(Optional.empty(), db.applicationData(id)); // still empty, as no data has been written to node

        db.createApplication(id, true);
        // Can be read as json, but no active session or last deployed session
        Optional<ApplicationData> applicationData = db.applicationData(id);
        assertTrue(applicationData.isPresent());
        assertEquals(id, applicationData.get().applicationId());
        assertFalse(applicationData.get().activeSession().isPresent());
        assertFalse(applicationData.get().lastDeployedSession().isPresent());

        // Prepare session 2, no active session
        try (var t = db.createWritePrepareTransaction(new CuratorTransaction(curator), id, 2, OptionalLong.empty(), false)) {
            t.commit();
        }
        // Activate session 2, last deployed session not present (not writing json)
        try (var t = db.createWriteActiveTransaction(new CuratorTransaction(curator), id, 2, false)) {
            t.commit();
        }
        // Can be read as session id only
        applicationData = db.applicationData(id);
        assertTrue(applicationData.isPresent());
        assertEquals(id, applicationData.get().applicationId());
        assertTrue(applicationData.get().activeSession().isPresent());
        assertEquals(2, applicationData.get().activeSession().get().longValue());
        assertFalse(applicationData.get().lastDeployedSession().isPresent());
        // Can be read as session data as well
        applicationData = db.applicationData(id);
        assertTrue(applicationData.isPresent());
        assertEquals(id, applicationData.get().applicationId());
        assertTrue(applicationData.get().activeSession().isPresent());
        assertEquals(2, applicationData.get().activeSession().get().longValue());
        assertFalse(applicationData.get().lastDeployedSession().isPresent());

        // Prepare session 3, last deployed session is still 2
        try (var t = db.createWritePrepareTransaction(new CuratorTransaction(curator), id, 3, OptionalLong.of(2), true)) {
            t.commit();
        }
        // Can be read as json, active session is still 2 and last deployed session is 3
        applicationData = db.applicationData(id);
        assertTrue(applicationData.isPresent());
        assertEquals(id, applicationData.get().applicationId());
        assertTrue(applicationData.get().activeSession().isPresent());
        assertEquals(2L, applicationData.get().activeSession().get().longValue());
        assertTrue(applicationData.get().lastDeployedSession().isPresent());
        assertEquals(3, applicationData.get().lastDeployedSession().get().longValue());

        try (var t = db.createWriteActiveTransaction(new CuratorTransaction(curator), id, 3, true)) {
            t.commit();
        }
        // Can be read as json, active session and last deployed session present
        applicationData = db.applicationData(id);
        assertTrue(applicationData.isPresent());
        assertEquals(id, applicationData.get().applicationId());
        assertTrue(applicationData.get().activeSession().isPresent());
        assertEquals(3, applicationData.get().activeSession().get().longValue());
        assertTrue(applicationData.get().lastDeployedSession().isPresent());
        assertEquals(3, applicationData.get().lastDeployedSession().get().longValue());
    }

}
