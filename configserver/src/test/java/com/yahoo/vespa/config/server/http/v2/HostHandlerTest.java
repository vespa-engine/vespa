// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.http.HandlerTest;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

import static com.yahoo.jdisc.http.HttpRequest.Method;
import static com.yahoo.vespa.config.server.http.HandlerTest.assertHttpStatusCodeErrorCodeAndMessage;

/**
 * @author hmusum
 */
public class HostHandlerTest {

    private static final String urlPrefix = "http://myhost:14000/application/v2/host/";
    private static final File testApp = new File("src/test/apps/app");

    private HostHandler handler;
    private final static TenantName mytenant = TenantName.from("mytenant");
    private final static Zone zone = Zone.defaultZone();
    private ApplicationRepository applicationRepository;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setup() throws IOException {
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        TenantRepository tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .build();
        tenantRepository.addTenant(mytenant);
        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withProvisioner(new MockProvisioner())
                .withOrchestrator(new OrchestratorMock())
                .withConfigserverConfig(configserverConfig)
                .build();
        handler = new HostHandler(HostHandler.testContext(), applicationRepository);
    }

    @Test
    public void require_correct_tenant_and_application_for_hostname() throws Exception {
        ApplicationId applicationId = applicationId();
        applicationRepository.deploy(testApp, new PrepareParams.Builder().applicationId(applicationId).build());
        String hostname = applicationRepository.getActiveApplicationSet(applicationId).get().getAllHosts().iterator().next();
        assertApplicationForHost(hostname, applicationId);
    }

    @Test
    public void require_that_handler_gives_error_for_unknown_hostname() throws Exception {
        String hostname = "unknown";
        assertErrorForUnknownHost(hostname,
                                  Response.Status.NOT_FOUND,
                                  "{\"error-code\":\"NOT_FOUND\",\"message\":\"Could not find any application using host '" + hostname + "'\"}");
    }

    @Test
    public void require_that_only_get_method_is_allowed() throws IOException {
        assertNotAllowed(com.yahoo.jdisc.http.HttpRequest.Method.PUT);
        assertNotAllowed(com.yahoo.jdisc.http.HttpRequest.Method.POST);
        assertNotAllowed(com.yahoo.jdisc.http.HttpRequest.Method.DELETE);
    }

    private void assertNotAllowed(Method method) throws IOException {
        String url = urlPrefix + "somehostname";
        executeAndAssertResponse(url, Response.Status.METHOD_NOT_ALLOWED,
                                 HttpErrorResponse.ErrorCode.METHOD_NOT_ALLOWED,
                "{\"error-code\":\"METHOD_NOT_ALLOWED\",\"message\":\"Method '" + method + "' is not supported\"}",
                                 method);
    }

    private void assertApplicationForHost(String hostname, ApplicationId expectedApplicationId) throws IOException {
        String url = urlPrefix + hostname;
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(url, Method.GET));
        HandlerTest.assertHttpStatusCodeAndMessage(response, Response.Status.OK,
                                                   "{\"tenant\":\"" + expectedApplicationId.tenant().value() + "\"," +
                                                   "\"application\":\"" + expectedApplicationId.application().value() + "\"," +
                                                   "\"environment\":\"" + HostHandlerTest.zone.environment().value() + "\"," +
                                                   "\"region\":\"" + HostHandlerTest.zone.region().value() + "\"," +
                                                   "\"instance\":\"" + expectedApplicationId.instance().value() + "\"}"
        );
    }

    private void assertErrorForUnknownHost(String hostname, int expectedStatus, String expectedResponse) throws IOException {
        String url = urlPrefix + hostname;
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(url, com.yahoo.jdisc.http.HttpRequest.Method.GET));
        assertHttpStatusCodeErrorCodeAndMessage(response, expectedStatus, HttpErrorResponse.ErrorCode.NOT_FOUND, expectedResponse);
    }

    private void executeAndAssertResponse(String url, int expectedStatus, HttpErrorResponse.ErrorCode errorCode,
                                          String expectedResponse, Method method) throws IOException {
        HttpResponse response = handler.handle(HttpRequest.createTestRequest(url, method));
        assertHttpStatusCodeErrorCodeAndMessage(response, expectedStatus, errorCode, expectedResponse);
    }

    private ApplicationId applicationId() {
        return ApplicationId.from(mytenant, ApplicationName.defaultName(), InstanceName.defaultName());
    }

}
