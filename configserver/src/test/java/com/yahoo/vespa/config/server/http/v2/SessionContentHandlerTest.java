// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.ApplicationConvergenceChecker;
import com.yahoo.vespa.config.server.application.LogServerLogGrabber;
import com.yahoo.vespa.config.server.http.SessionContentHandlerTestBase;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;

import java.io.InputStream;
import java.util.concurrent.Executor;

/**
 * @author lulf
 * @since 5.1
 */
public class SessionContentHandlerTest extends SessionContentHandlerTestBase {
    private static final TenantName tenant = TenantName.from("contenttest");
    private SessionContentHandler handler = null;
    
    @Before
    public void setupHandler() throws Exception {
        handler = createHandler();
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/";
        baseUrl = "http://foo:1337/application/v2/tenant/" + tenant + "/session/1/content/";
    }

    protected HttpResponse doRequest(HttpRequest.Method method, String path) {
        return doRequest(method, path, 1l);
    }

    protected HttpResponse doRequest(HttpRequest.Method method, String path, long sessionId) {
        return handler.handle(SessionHandlerTest.createTestRequest(pathPrefix, method, Cmd.CONTENT, sessionId, path));
    }

    protected HttpResponse doRequest(HttpRequest.Method method, String path, InputStream data) {
        return doRequest(method, path, 1l, data);
    }

    protected HttpResponse doRequest(HttpRequest.Method method, String path, long sessionId, InputStream data) {
        return handler.handle(SessionHandlerTest.createTestRequest(pathPrefix, method, Cmd.CONTENT, sessionId, path, data));
    }

    private SessionContentHandler createHandler() throws Exception {
        TestTenantBuilder testTenantBuilder = new TestTenantBuilder();
        testTenantBuilder.createTenant(tenant).getLocalSessionRepo().addSession(new MockSession(1l, FilesApplicationPackage.fromFile(createTestApp())));
        return new SessionContentHandler(new Executor() {
            @SuppressWarnings("NullableProblems")
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        }, AccessLog.voidAccessLog(), testTenantBuilder.createTenants(),
                                         new ApplicationRepository(testTenantBuilder.createTenants(),
                                                                   HostProvisionerProvider.withProvisioner(new SessionActiveHandlerTest.MockProvisioner()),
                                                                   new MockCurator(),
                                                                   new LogServerLogGrabber(),
                                                                   new ApplicationConvergenceChecker()));
    }
}
