// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.PathProvider;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Test;

import com.yahoo.config.provision.TenantName;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 * @since 5.1.14
 */
public class SessionRepoTest {
    @Test
    public void require_that_sessionrepo_is_initialized() {
        SessionRepo<TestSession> sessionRepo = new SessionRepo<>();
        assertNull(sessionRepo.getSession(1L));
        sessionRepo.addSession(new TestSession(1));
        assertThat(sessionRepo.getSession(1L).getSessionId(), is(1l));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_adding_existing_session_fails() {
        SessionRepo<TestSession> sessionRepo = new SessionRepo<>();
        final TestSession session = new TestSession(1);
        sessionRepo.addSession(session);
        sessionRepo.addSession(session);
    }

    private class TestSession extends Session {
        public TestSession(long sessionId) {
            super(TenantName.from("default"),
                  sessionId,
                  new MockSessionZKClient(new MockCurator(), new PathProvider(Path.createRoot()).getSessionDir(sessionId)));
        }
    }
}
