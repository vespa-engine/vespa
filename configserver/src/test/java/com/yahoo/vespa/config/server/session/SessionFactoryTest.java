// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.google.common.io.Files;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.io.IOUtils;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.*;
import com.yahoo.vespa.config.server.http.CompressedApplicationInputStream;
import com.yahoo.vespa.config.server.http.CompressedApplicationInputStreamTest;

import com.yahoo.vespa.config.server.http.v2.ApplicationApiHandler;
import com.yahoo.vespa.config.server.tenant.TestWithTenant;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author lulf
 * @since 5.1
 */
public class SessionFactoryTest extends TestWithTenant {
    private SessionFactory factory;

    @Before
    public void setup_test() throws Exception {
        factory = tenant.getSessionFactory();
    }

    @Test
    public void require_that_session_can_be_created() throws IOException {
        LocalSession session = getLocalSession();
        assertNotNull(session);
        assertThat(session.getSessionId(), is(2l));
        assertTrue(session.getCreateTime() > 0);
    }

    @Test
    public void require_that_application_name_is_set_in_application_package() throws IOException, JSONException {
        LocalSession session = getLocalSession("book");
        assertNotNull(session);
        ApplicationFile meta = session.getApplicationFile(Path.createRoot().append(".applicationMetaData"), LocalSession.Mode.READ);
        assertTrue(meta.exists());
        JSONObject json = new JSONObject(IOUtils.readAll(meta.createReader()));
        assertThat(json.getJSONObject("application").getString("name"), is("book"));
    }

    @Test
    public void require_that_session_can_be_created_from_existing() throws IOException {
        LocalSession session = getLocalSession();
        assertNotNull(session);
        assertThat(session.getSessionId(), is(2l));
        LocalSession session2 = factory.createSessionFromExisting(session, new BaseDeployLogger(), TimeoutBudgetTest.day());
        assertNotNull(session2);
        assertThat(session2.getSessionId(), is(3l));
    }

    @Test(expected = RuntimeException.class)
    public void require_that_invalid_app_dir_is_handled() throws IOException {
        factory.createSession(new File("doesnotpointtoavaliddir"), "music", TimeoutBudgetTest.day());
    }

    private LocalSession getLocalSession() throws IOException {
        return getLocalSession("music");
    }

    private LocalSession getLocalSession(String appName) throws IOException {
        CompressedApplicationInputStream app = CompressedApplicationInputStream.createFromCompressedStream(
                new FileInputStream(CompressedApplicationInputStreamTest.createTarFile()), ApplicationApiHandler.APPLICATION_X_GZIP);
        return factory.createSession(app.decompress(Files.createTempDir()), appName, TimeoutBudgetTest.day());
    }
}
