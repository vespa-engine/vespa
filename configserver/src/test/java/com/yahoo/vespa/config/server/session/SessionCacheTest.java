// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Test;

import com.yahoo.config.provision.TenantName;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 */
public class SessionCacheTest {

    @Test
    public void require_that_sessionrepo_is_initialized() {
        SessionCache<TestSession> sessionCache = new SessionCache<>();
        assertNull(sessionCache.getSession(1L));
        sessionCache.addSession(new TestSession(1));
        assertThat(sessionCache.getSession(1L).getSessionId(), is(1L));
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_adding_existing_session_fails() {
        SessionCache<TestSession> sessionCache = new SessionCache<>();
        final TestSession session = new TestSession(1);
        sessionCache.addSession(session);
        sessionCache.addSession(session);
    }

    private class TestSession extends Session {
        TestSession(long sessionId) {
            super(TenantName.defaultName(),
                  sessionId,
                  new MockSessionZKClient(new MockCurator(), TenantName.defaultName(), sessionId));
        }
    }
}
