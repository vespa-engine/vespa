// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import ai.vespa.http.HttpURL.Path;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.MockProvisioner;
import com.yahoo.vespa.config.server.application.CompressedApplicationInputStreamTest;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.session.Session;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import org.apache.hc.core5.http.ContentType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.OK;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static com.yahoo.vespa.config.server.http.HandlerTest.assertHttpStatusCodeErrorCodeAndMessage;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author hmusum
 */
public class SessionCreateHandlerTest extends SessionHandlerTest {

    private static final TenantName tenant = TenantName.from("test");
    private static final HashMap<String, String> postHeaders = new HashMap<>();

    ApplicationRepository applicationRepository;

    private String pathPrefix = "/application/v2/session/";
    private String createdMessage = " created.\"";
    private String tenantMessage = "";

    static {
        postHeaders.put(ApplicationApiHandler.contentTypeHeader, ApplicationApiHandler.APPLICATION_X_GZIP);
    }

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private ConfigserverConfig configserverConfig;

    @Before
    public void setupRepo() throws IOException {
        configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder().getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder().getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();
        TenantRepository tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .build();
        applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withProvisioner(new MockProvisioner())
                .withOrchestrator(new OrchestratorMock())
                .build();
        tenantRepository.addTenant(tenant);
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/";
        createdMessage = " for tenant '" + tenant + "' created.\"";
        tenantMessage = ",\"tenant\":\"test\"";
    }

    @Ignore
    @Test
    public void require_that_from_parameter_cannot_be_set_if_data_in_request() throws IOException {
        HttpRequest request = post(Collections.singletonMap("from", "active"));
        HttpResponse response = createHandler().handle(request);
        assertHttpStatusCodeErrorCodeAndMessage(response, BAD_REQUEST, HttpErrorResponse.ErrorCode.BAD_REQUEST, "Parameter 'from' is illegal for POST");
    }

    @Test
    public void require_that_post_request_must_contain_data() throws IOException {
        HttpResponse response = createHandler().handle(post());
        assertHttpStatusCodeErrorCodeAndMessage(response, BAD_REQUEST, HttpErrorResponse.ErrorCode.BAD_REQUEST, "Request contains no data");
    }

    @Test
    public void require_that_post_request_must_have_correct_content_type() throws IOException {
        HashMap<String, String> headers = new HashMap<>(); // no Content-Type header
        File outFile = CompressedApplicationInputStreamTest.createTarFile(temporaryFolder.getRoot().toPath());
        HttpResponse response = createHandler().handle(post(outFile, headers, null));
        assertHttpStatusCodeErrorCodeAndMessage(response, BAD_REQUEST, HttpErrorResponse.ErrorCode.BAD_REQUEST, "Request contains no Content-Type header");
    }

    private void assertIllegalFromParameter(String fromValue) throws IOException {
        File outFile = CompressedApplicationInputStreamTest.createTarFile(temporaryFolder.getRoot().toPath());
        HttpRequest request = post(outFile, postHeaders, Collections.singletonMap("from", fromValue));
        assertHttpStatusCodeErrorCodeAndMessage(createHandler().handle(request), BAD_REQUEST, HttpErrorResponse.ErrorCode.BAD_REQUEST, "Parameter 'from' has illegal value '" + fromValue + "'");
    }

    @Test
    public void require_that_prepare_url_is_returned_on_success() throws IOException {
        File outFile = CompressedApplicationInputStreamTest.createTarFile(temporaryFolder.getRoot().toPath());
        Map<String, String> parameters = Collections.singletonMap("name", "foo");
        HttpResponse response = createHandler().handle(post(outFile, postHeaders, parameters));
        assertNotNull(response);
        assertEquals(OK, response.getStatus());
        assertEquals(SessionHandlerTest.getRenderedString(response),
                   "{\"log\":[]" + tenantMessage + ",\"session-id\":\"2\",\"prepared\":\"http://" +
                              hostname + ":" + port + pathPrefix + "2/prepared\",\"content\":\"http://" +
                              hostname + ":" + port + pathPrefix + "2/content/\",\"message\":\"Session 2" + createdMessage + "}");
    }

    @Test
    public void require_that_handler_does_not_support_get() throws IOException {
        HttpResponse response = createHandler().handle(HttpRequest.createTestRequest(pathPrefix, GET));
        assertHttpStatusCodeErrorCodeAndMessage(response, METHOD_NOT_ALLOWED,
                                                HttpErrorResponse.ErrorCode.METHOD_NOT_ALLOWED,
                                                "Method 'GET' is not supported");
    }

    @Test
    public void require_internal_error_when_exception() throws IOException {
        File outFile = CompressedApplicationInputStreamTest.createTarFile(temporaryFolder.getRoot().toPath());
        new FileWriter(outFile).write("rubbish");
        HttpResponse response = createHandler().handle(post(outFile));
        assertHttpStatusCodeErrorCodeAndMessage(response, INTERNAL_SERVER_ERROR,
                                                HttpErrorResponse.ErrorCode.INTERNAL_SERVER_ERROR,
                                                "Unable to create compressed application stream");
    }

    @Test
    public void require_that_handler_unpacks_application() throws IOException {
        File outFile = CompressedApplicationInputStreamTest.createTarFile(temporaryFolder.getRoot().toPath());
        createHandler().handle(post(outFile));
        ApplicationFile applicationFile = applicationRepository.getApplicationFileFromSession(tenant, 2, Path.parse("services.xml"), Session.Mode.READ);
        assertTrue(applicationFile.exists());
    }

    @Test
    public void require_that_application_urls_can_be_given_as_from_parameter() throws Exception {
        ApplicationId applicationId = ApplicationId.from(tenant.value(), "foo", "quux");
        HttpRequest request = post(Collections.singletonMap(
                "from",
                "http://myhost:40555/application/v2/tenant/" + tenant + "/application/foo/environment/test/region/baz/instance/quux"));
        assertEquals(applicationId, SessionCreateHandler.getFromApplicationId(request));
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

    @Test
    public void require_that_content_type_is_parsed_correctly() throws FileNotFoundException {
        HttpRequest request = post(new ByteArrayInputStream("foo".getBytes(StandardCharsets.UTF_8)),
                                   Map.of("Content-Type", "multipart/form-data; charset=ISO-8859-1; boundary=g5gJAzUWl_t6"),
                                   Collections.emptyMap());

        // Valid header should validate ok
        SessionCreateHandler.validateDataAndHeader(request, List.of(ContentType.MULTIPART_FORM_DATA.getMimeType()));

        // Accepting only application/json should fail:
        try {
            SessionCreateHandler.validateDataAndHeader(request, List.of(ContentType.APPLICATION_JSON.getMimeType()));
            fail("Request contained invalid content type, but validated ok");
        } catch (Exception expected) {}
    }

    private SessionCreateHandler createHandler() {
        return new SessionCreateHandler(SessionCreateHandler.testContext(),
                                        applicationRepository,
                                        configserverConfig);
    }

    private HttpRequest post() throws FileNotFoundException {
        return post((InputStream) null, postHeaders, new HashMap<>());
    }

    private HttpRequest post(File file) throws FileNotFoundException {
        return post(file, postHeaders, new HashMap<>());
    }

    private HttpRequest post(File file, Map<String, String> headers, Map<String, String> parameters) throws FileNotFoundException {
        return post(file == null ? null : new FileInputStream(file), headers, parameters);
    }

    private HttpRequest post(InputStream data, Map <String, String > headers, Map < String, String > parameters) throws FileNotFoundException {
        HttpRequest request = HttpRequest.createTestRequest("http://" + hostname + ":" + port + "/application/v2/tenant/" + tenant + "/session",
                POST, data, parameters);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.getJDiscRequest().headers().put(entry.getKey(), entry.getValue());
        }
        return request;
    }

    private HttpRequest post(Map<String, String> parameters) throws FileNotFoundException {
        return post((InputStream) null, new HashMap<>(), parameters);
    }
}
