// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.model.provision.InMemoryProvisioner;
import com.yahoo.config.provision.ApplicationLockException;
import com.yahoo.config.provision.ParentHostUnavailableException;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.ThreadedHttpRequestHandler.Context;
import com.yahoo.jdisc.http.HttpRequest.Method;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.test.ManualClock;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.provision.HostProvisionerProvider;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.yahoo.yolean.Exceptions.uncheck;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author jonmv
 */
class ApplicationApiHandlerTest {

    private static final TenantName tenant = TenantName.from("test");
    private static final Map<String, String> appPackage = Map.of("services.xml",
                                                                 """
                                                                 <services version='1.0'>
                                                                   <container id='jdisc' version='1.0'>
                                                                     <nodes count='2' />
                                                                   </container>
                                                                 </services>
                                                                 """,

                                                                 "deployment.xml",
                                                                 """
                                                                 <deployment version='1.0' />
                                                                 """);
    static final String minimalPrepareParams = """
                                               {
                                                 "containerEndpoints": [
                                                   {
                                                     "clusterId": "jdisc",
                                                     "scope": "zone",
                                                     "names": ["zone.endpoint"],
                                                     "routingMethod": "exclusive",
                                                     "authMethod": "mtls"
                                                   }
                                                 ]
                                               }
                                               """;

    private final Curator curator = new MockCurator();
    private ApplicationRepository applicationRepository;

    private MockProvisioner provisioner;
    private ConfigserverConfig configserverConfig;
    private TenantRepository tenantRepository;
    private ApplicationApiHandler handler;

    @TempDir
    public Path dbDir, defsDir, refsDir;

    @BeforeEach
    public void setupRepo() {
        configserverConfig = new ConfigserverConfig.Builder()
                .hostedVespa(true)
                .configServerDBDir(dbDir.toString())
                .configDefinitionsDir(defsDir.toString())
                .fileReferencesDir(refsDir.toString())
                .build();
        Clock clock = new ManualClock();
        provisioner = new MockProvisioner().hostProvisioner(new InMemoryProvisioner(4, false));
        tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .withCurator(curator)
                .withHostProvisionerProvider(HostProvisionerProvider.withProvisioner(provisioner, configserverConfig))
                .build();
        tenantRepository.addTenant(tenant);
        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withOrchestrator(new OrchestratorMock())
                .withClock(clock)
                .withConfigserverConfig(configserverConfig)
                .build();
        handler = new ApplicationApiHandler(new Context(Runnable::run, null),
                                            applicationRepository,
                                            configserverConfig,
                                            Zone.defaultZone());
    }

    private HttpResponse put(long sessionId, Map<String, String> parameters) {
        var request = com.yahoo.container.jdisc.HttpRequest.createTestRequest("http://host:123/application/v2/tenant/" + tenant + "/prepareandactivate/" + sessionId,
                                                                              Method.PUT,
                                                                              InputStream.nullInputStream(),
                                                                              parameters);
        return handler.handle(request);
    }

    private HttpResponse post(String json, byte[] appZip, Map<String, String> parameters) throws IOException {
        HttpEntity entity = MultipartEntityBuilder.create()
                                                  .addTextBody("prepareParams", json, ContentType.APPLICATION_JSON)
                                                  .addBinaryBody("applicationPackage", appZip, ContentType.create("application/zip"), "applicationZip")
                                                  .build();
        var request = com.yahoo.container.jdisc.HttpRequest.createTestRequest("http://host:123/application/v2/tenant/" + tenant + "/prepareandactivate",
                                                                              Method.POST,
                                                                              entity.getContent(),
                                                                              parameters);
        request.getJDiscRequest().headers().add("Content-Type", entity.getContentType());
        return handler.handle(request);
    }

    private static byte[] zip(Map<String, String> files) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buffer)) {
            files.forEach((name, content) -> uncheck(() -> {
                zip.putNextEntry(new ZipEntry(name));
                zip.write(content.getBytes(UTF_8));
            }));
        }
        return buffer.toByteArray();
    }

    private static void verifyResponse(HttpResponse response, int expectedStatusCode, String expectedBody) throws IOException {
        String body = new ByteArrayOutputStream() {{ response.render(this); }}.toString(UTF_8);
        assertEquals(expectedStatusCode, response.getStatus(), "Status code should match. Response was:\n" + body);
        assertEquals(SlimeUtils.toJson(SlimeUtils.jsonToSlimeOrThrow(expectedBody).get(), false),
                     SlimeUtils.toJson(SlimeUtils.jsonToSlimeOrThrow(body).get(), false));
    }

    @Test
    void testMinimalDeployment() throws Exception {
        verifyResponse(post(minimalPrepareParams, zip(appPackage), Map.of()),
                       200,
                       """
                       {
                         "log": [ ],
                         "message": "Session 2 for tenant 'test' prepared and activated.",
                         "session-id": "2",
                         "activated": true,
                         "tenant": "test",
                         "url": "http://host:123/application/v2/tenant/test/application/default/environment/prod/region/default/instance/default",
                         "configChangeActions": {
                           "restart": [ ],
                           "refeed": [ ],
                           "reindex": [ ]
                         }
                       }
                       """);
    }

    @Test
    void testBadZipDeployment() throws Exception {
        verifyResponse(post("{ }", Arrays.copyOf(zip(appPackage), 13), Map.of()),
                       400,
                       """
                       {
                         "error-code": "BAD_REQUEST",
                         "message": "Error preprocessing application package for test.default, session 2: services.xml does not exist in application package. There are 1 files in the directory"
                       }
                       """);
    }

    @Test
    void testPrepareFailure() throws Exception {
        provisioner.transientFailureOnPrepare();
        verifyResponse(post(minimalPrepareParams, zip(appPackage), Map.of()),
                       409,
                       """
                       {
                         "error-code": "LOAD_BALANCER_NOT_READY",
                         "message": "Unable to create load balancer: some internal exception"
                       }
                       """);
    }

    @Test
    void testActivateInvalidSession() throws Exception {
        verifyResponse(put(2, Map.of()),
                       404,
                       """
                       {
                         "error-code": "NOT_FOUND",
                         "message": "Local session 2 for 'test' was not found"
                       }
                       """);
    }

    @Test
    void testActivationFailuresAndRetries() throws Exception {
        // Prepare session 2, and activate it successfully.
        verifyResponse(post(minimalPrepareParams, zip(appPackage), Map.of()),
                       200,
                       """
                       {
                         "log": [ ],
                         "message": "Session 2 for tenant 'test' prepared and activated.",
                         "session-id": "2",
                         "activated": true,
                         "tenant": "test",
                         "url": "http://host:123/application/v2/tenant/test/application/default/environment/prod/region/default/instance/default",
                         "configChangeActions": {
                           "restart": [ ],
                           "refeed": [ ],
                           "reindex": [ ]
                         }
                       }
                       """);

        // Prepare session 3, but fail on hosts; this session will be activated later.
        provisioner.activationFailure(new ParentHostUnavailableException("host still booting"));
        verifyResponse(post(minimalPrepareParams, zip(appPackage), Map.of()),
                       200,
                       """
                       {
                         "log": [ ],
                         "message": "Session 3 for tenant 'test' prepared, but activation failed: host still booting",
                         "session-id": "3",
                         "activated": false,
                         "tenant": "test",
                         "url": "http://host:123/application/v2/tenant/test/application/default/environment/prod/region/default/instance/default",
                         "configChangeActions": {
                           "restart": [ ],
                           "refeed": [ ],
                           "reindex": [ ]
                         }
                       }
                       """);

        // Prepare session 4, but fail on lock; this session will become outdated later.
        provisioner.activationFailure(new ApplicationLockException("lock timeout"));
        verifyResponse(post(minimalPrepareParams, zip(appPackage), Map.of()),
                       200,
                       """
                       {
                         "log": [ ],
                         "message": "Session 4 for tenant 'test' prepared, but activation failed: lock timeout",
                         "session-id": "4",
                         "activated": false,
                         "tenant": "test",
                         "url": "http://host:123/application/v2/tenant/test/application/default/environment/prod/region/default/instance/default",
                         "configChangeActions": {
                           "restart": [ ],
                           "refeed": [ ],
                           "reindex": [ ]
                         }
                       }
                       """);

        // Prepare session 4, but fail with some other exception, which we won't retry.
        provisioner.activationFailure(new RuntimeException("some other exception"));
        verifyResponse(post(minimalPrepareParams, zip(appPackage), Map.of()),
                       500,
                       """
                       {
                         "error-code": "INTERNAL_SERVER_ERROR",
                         "message": "some other exception"
                       }
                       """);

        // Retry only activation of session 3, but fail again with hosts.
        provisioner.activationFailure(new ParentHostUnavailableException("host still booting"));
        verifyResponse(put(3, Map.of()),
                       409,
                       """
                       {
                         "error-code": "PARENT_HOST_NOT_READY",
                         "message": "host still booting"
                       }
                       """);

        // Retry only activation of session 3, but fail again with lock.
        provisioner.activationFailure(new ApplicationLockException("lock timeout"));
        verifyResponse(put(3, Map.of()),
                       500,
                       """
                       {
                         "error-code": "APPLICATION_LOCK_FAILURE",
                         "message": "lock timeout"
                       }
                       """);

        // Retry only activation of session 3, and succeed!
        provisioner.activationFailure(null);
        verifyResponse(put(3, Map.of()),
                       200,
                       """
                       {
                         "message": "Session 3 for test.default.default activated"
                       }
                       """);

        // Retry only activation of session 4, but fail because it is now based on an outdated session.
        verifyResponse(put(4, Map.of()),
                       409,
                       """
                       {
                         "error-code": "ACTIVATION_CONFLICT",
                         "message": "app:test.default.default Cannot activate session 4 because the currently active session (3) has changed since session 4 was created (was 2 at creation time)"
                       }
                       """);

        // Retry activation of session 3 again, and fail.
        verifyResponse(put(3, Map.of()),
                       400,
                       """
                       {
                         "error-code": "BAD_REQUEST",
                         "message": "app:test.default.default Session 3 is already active"
                       }
                       """);
    }

}
