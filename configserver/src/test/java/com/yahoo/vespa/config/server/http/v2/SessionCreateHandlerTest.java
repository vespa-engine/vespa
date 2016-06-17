// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.vespa.config.server.*;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.config.server.application.MemoryApplicationRepo;
import com.yahoo.vespa.config.server.http.SessionCreateHandlerTestBase;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.session.*;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static org.junit.Assert.*;

import static com.yahoo.jdisc.http.HttpRequest.Method.*;

/**
 * @author hmusum
 * @since 5.1
 */
public class SessionCreateHandlerTest extends SessionCreateHandlerTestBase {

    private static final TenantName tenant = TenantName.from("test");

    @Before
    public void setupRepo() throws Exception {
        applicationRepo = new MemoryApplicationRepo();
        localSessionRepo = new LocalSessionRepo(applicationRepo);
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/";
        createdMessage = " for tenant '" + tenant + "' created.\"";
        tenantMessage = ",\"tenant\":\"test\"";
    }

    @Test
    public void require_that_application_urls_can_be_given_as_from_parameter() throws Exception {
        localSessionRepo.addSession(new SessionHandlerTest.MockSession(2l, FilesApplicationPackage.fromFile(testApp)));
        ApplicationId fooId = new ApplicationId.Builder()
                              .tenant(tenant)
                              .applicationName("foo")
                              .instanceName("quux")
                              .build();
        applicationRepo.createPutApplicationTransaction(fooId, 2).commit();
        assertFromParameter("3", "http://myhost:40555/application/v2/tenant/" + tenant + "/application/foo/environment/test/region/baz/instance/quux");
        localSessionRepo.addSession(new SessionHandlerTest.MockSession(5l, FilesApplicationPackage.fromFile(testApp)));
        ApplicationId bioId = new ApplicationId.Builder()
                              .tenant(tenant)
                              .applicationName("foobio")
                              .instanceName("quux")
                              .build();
        applicationRepo.createPutApplicationTransaction(bioId, 5).commit();
        assertFromParameter("6", "http://myhost:40555/application/v2/tenant/" + tenant + "/application/foobio/environment/staging/region/baz/instance/quux");
    }

    @Test
    public void require_that_from_parameter_must_be_valid() throws IOException {
        assertIllegalFromParameter("active");
        assertIllegalFromParameter("");
        assertIllegalFromParameter("http://host:4013/application/v2/tenant/" + tenant + "/application/lol");
        assertIllegalFromParameter("http://host:4013/application/v2/tenant/" + tenant + "/application/foo/environment/prod");
        assertIllegalFromParameter("http://host:4013/application/v2/tenant/" + tenant + "/application/foo/environment/prod/region/baz");
        assertIllegalFromParameter("http://host:4013/application/v2/tenant/" + tenant + "/application/foo/environment/prod/region/baz/instance");
    }

    @Override
    public SessionCreateHandler createHandler() {
        try {
            return createHandler(new MockSessionFactory());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }

    @Override
    public SessionCreateHandler createHandler(SessionFactory sessionFactory) {
        try {
            TestTenantBuilder testBuilder = new TestTenantBuilder();
            testBuilder.createTenant(tenant).withSessionFactory(sessionFactory)
                                            .withLocalSessionRepo(localSessionRepo)
                                            .withApplicationRepo(applicationRepo);
            return createHandler(testBuilder.createTenants());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    SessionCreateHandler createHandler(Tenants tenants) {
        return new SessionCreateHandler(new Executor() {
            @SuppressWarnings("NullableProblems")
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        }, AccessLog.voidAccessLog(), tenants, new ConfigserverConfig(new ConfigserverConfig.Builder()));
    }

    @Override
    public HttpRequest post() throws FileNotFoundException {
        return post(null, postHeaders, new HashMap<String, String>());
    }

    @Override
    public HttpRequest post(File file) throws FileNotFoundException {
        return post(file, postHeaders, new HashMap<String, String>());
    }

    @Override
    public HttpRequest post(File file, Map<String, String> headers, Map<String, String> parameters) throws FileNotFoundException {
        HttpRequest request = HttpRequest.createTestRequest("http://" + hostname + ":" + port + "/application/v2/tenant/" + tenant + "/session",
                POST,
                file == null ? null : new FileInputStream(file),
                parameters);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.getJDiscRequest().headers().put(entry.getKey(), entry.getValue());
        }
        return request;
    }

    @Override
    public HttpRequest post(Map<String, String> parameters) throws FileNotFoundException {
        return post(null, new HashMap<String, String>(), parameters);
    }
}
