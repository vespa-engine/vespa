// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

/**
 * @author jonmv
 */
public class ApplicationCuratorDatabaseTest {

    private final MockCurator curator = new MockCurator();

    @Test
    public void testReindexingStatusSerialization() {
        ApplicationId id = ApplicationId.defaultId();
        ApplicationCuratorDatabase db = new ApplicationCuratorDatabase(id.tenant(), curator);

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
        ApplicationCuratorDatabase db = new ApplicationCuratorDatabase(id.tenant(), curator);

        assertEquals(Optional.empty(), db.applicationData(id));

        db.createApplicationInOldFormat(id);
        assertEquals(Optional.empty(), db.applicationData(id)); // still empty, as no data has been written to node
        deleteApplication(db, id);

        db.createApplication(id);
        // Can be read as json, but no active session or last deployed session
        Optional<ApplicationData> applicationData = db.applicationData(id);
        assertTrue(applicationData.isPresent());
        assertEquals(id, applicationData.get().applicationId());
        assertFalse(applicationData.get().activeSession().isPresent());
        assertFalse(applicationData.get().lastDeployedSession().isPresent());

        // Prepare session 2, no active session
        prepareSessionOldFormat(db, id, 2, OptionalLong.empty());
        // Activate session 2, last deployed session not present (not writing json)
        activateSessionOldFormat(db, id, 2);
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
        prepareSession(db, id, 3, OptionalLong.of(2));
        // Can be read as json, active session is still 2 and last deployed session is 3
        applicationData = db.applicationData(id);
        assertTrue(applicationData.isPresent());
        assertEquals(id, applicationData.get().applicationId());
        assertTrue(applicationData.get().activeSession().isPresent(), applicationData.get().toString());
        assertEquals(2L, applicationData.get().activeSession().get().longValue());
        assertTrue(applicationData.get().lastDeployedSession().isPresent());
        assertEquals(3, applicationData.get().lastDeployedSession().get().longValue());

        activateSession(db, id, 3);
        // Can be read as json, active session and last deployed session present
        applicationData = db.applicationData(id);
        assertTrue(applicationData.isPresent());
        assertEquals(id, applicationData.get().applicationId());
        assertTrue(applicationData.get().activeSession().isPresent());
        assertEquals(3, applicationData.get().activeSession().get().longValue());
        assertTrue(applicationData.get().lastDeployedSession().isPresent());
        assertEquals(3, applicationData.get().lastDeployedSession().get().longValue());

        // createApplication should not overwrite the node if it already exists
        db.createApplication(id);
        // Can be read as json, active session and last deployed session present
        applicationData = db.applicationData(id);
        assertTrue(applicationData.isPresent());
        assertEquals(id, applicationData.get().applicationId());
        assertTrue(applicationData.get().activeSession().isPresent());
        assertEquals(3, applicationData.get().activeSession().get().longValue());
        assertTrue(applicationData.get().lastDeployedSession().isPresent());
        assertEquals(3, applicationData.get().lastDeployedSession().get().longValue());
    }


    private void deleteApplication(ApplicationCuratorDatabase db, ApplicationId applicationId) {
        try (var t = db.createDeleteTransaction(applicationId)) {
            t.commit();
        }
    }

    private void prepareSession(ApplicationCuratorDatabase db, ApplicationId applicationId, long sessionId, OptionalLong activesSessionId) {
        try (var t = db.createWritePrepareTransaction(new CuratorTransaction(curator),
                                                      applicationId,
                                                      sessionId,
                                                      activesSessionId)) {
            t.commit();
        }
    }

    private void prepareSessionOldFormat(ApplicationCuratorDatabase db, ApplicationId applicationId, long sessionId, OptionalLong activesSessionId) {
        return; // Nothing to do, just return
    }

    private void activateSession(ApplicationCuratorDatabase db, ApplicationId applicationId, long sessionId) {
        try (var t = db.createWriteActiveTransaction(new CuratorTransaction(curator), applicationId, sessionId)) {
            t.commit();
        }
    }

    private void activateSessionOldFormat(ApplicationCuratorDatabase db, ApplicationId applicationId, long sessionId) {
        try (var t = db.createWriteActiveTransactionInOldFormat(new CuratorTransaction(curator), applicationId, sessionId)) {
            t.commit();
        }
    }

}
