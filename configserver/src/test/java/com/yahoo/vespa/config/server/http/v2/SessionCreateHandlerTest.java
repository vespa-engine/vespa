// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.application.MemoryTenantApplications;
import com.yahoo.vespa.config.server.application.TenantApplications;
import com.yahoo.vespa.config.server.http.CompressedApplicationInputStreamTest;
import com.yahoo.vespa.config.server.http.HandlerTest;
import com.yahoo.vespa.config.server.http.HttpErrorResponse;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.session.LocalSessionRepo;
import com.yahoo.vespa.config.server.tenant.TenantBuilder;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.INTERNAL_SERVER_ERROR;
import static com.yahoo.jdisc.Response.Status.METHOD_NOT_ALLOWED;
import static com.yahoo.jdisc.Response.Status.OK;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.POST;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * @author hmusum
 */
public class SessionCreateHandlerTest extends SessionHandlerTest {

    private static final TenantName tenant = TenantName.from("test");
    private static final HashMap<String, String> postHeaders = new HashMap<>();

    private final TestComponentRegistry componentRegistry = new TestComponentRegistry.Builder().build();
    private final Clock clock = componentRegistry.getClock();

    private String pathPrefix = "/application/v2/session/";
    private String createdMessage = " created.\"";
    private String tenantMessage = "";

    public File testApp = new File("src/test/apps/app");
    private LocalSessionRepo localSessionRepo;
    private TenantApplications applicationRepo;
    private TenantRepository tenantRepository;
    private MockSessionFactory sessionFactory;

    static {
        postHeaders.put(ApplicationApiHandler.contentTypeHeader, ApplicationApiHandler.APPLICATION_X_GZIP);
    }

    @Before
    public void setupRepo() {
        applicationRepo = new MemoryTenantApplications();
        localSessionRepo = new LocalSessionRepo(Clock.systemUTC());
        tenantRepository = new TenantRepository(componentRegistry, false);
        sessionFactory = new MockSessionFactory();
        TenantBuilder tenantBuilder = TenantBuilder.create(componentRegistry, tenant)
                .withSessionFactory(sessionFactory)
                .withLocalSessionRepo(localSessionRepo)
                .withApplicationRepo(applicationRepo);
        tenantRepository.addTenant(tenantBuilder);
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/";
        createdMessage = " for tenant '" + tenant + "' created.\"";
        tenantMessage = ",\"tenant\":\"test\"";
    }

    @Ignore
    @Test
    public void require_that_from_parameter_cannot_be_set_if_data_in_request() throws IOException {
        HttpRequest request = post(Collections.singletonMap("from", "active"));
        HttpResponse response = createHandler().handle(request);
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, BAD_REQUEST, HttpErrorResponse.errorCodes.BAD_REQUEST, "Parameter 'from' is illegal for POST");
    }

    @Test
    public void require_that_post_request_must_contain_data() throws IOException {
        HttpResponse response = createHandler().handle(post());
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, BAD_REQUEST, HttpErrorResponse.errorCodes.BAD_REQUEST, "Request contains no data");
    }

    @Test
    public void require_that_post_request_must_have_correct_content_type() throws IOException {
        HashMap<String, String> headers = new HashMap<>(); // no Content-Type header
        File outFile = CompressedApplicationInputStreamTest.createTarFile();
        HttpResponse response = createHandler().handle(post(outFile, headers, null));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, BAD_REQUEST, HttpErrorResponse.errorCodes.BAD_REQUEST, "Request contains no Content-Type header");
    }

    @Test
    public void require_that_application_name_is_given_from_parameter() throws IOException {
        Map<String, String> params = Collections.singletonMap("name", "ulfio");
        File outFile = CompressedApplicationInputStreamTest.createTarFile();
        createHandler().handle(post(outFile, postHeaders, params));
        assertTrue(sessionFactory.createCalled);
        assertThat(sessionFactory.applicationName, is("ulfio"));
    }

    private void assertFromParameter(String expected, String from) throws IOException {
        HttpRequest request = post(Collections.singletonMap("from", from));
        sessionFactory.applicationPackage = testApp;
        HttpResponse response = createHandler().handle(request);
        assertNotNull(response);
        assertThat(response.getStatus(), is(OK));
        assertTrue(sessionFactory.createFromCalled);
        assertThat(SessionHandlerTest.getRenderedString(response),
                   is("{\"log\":[]" + tenantMessage + ",\"session-id\":\"" + expected + "\",\"prepared\":\"http://" + hostname + ":" + port + pathPrefix +
                              expected + "/prepared\",\"content\":\"http://" + hostname + ":" + port + pathPrefix +
                              expected + "/content/\",\"message\":\"Session " + expected + createdMessage + "}"));
    }

    private void assertIllegalFromParameter(String fromValue) throws IOException {
        File outFile = CompressedApplicationInputStreamTest.createTarFile();
        HttpRequest request = post(outFile, postHeaders, Collections.singletonMap("from", fromValue));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(createHandler().handle(request), BAD_REQUEST, HttpErrorResponse.errorCodes.BAD_REQUEST, "Parameter 'from' has illegal value '" + fromValue + "'");
    }

    @Test
    public void require_that_prepare_url_is_returned_on_success() throws IOException {
        File outFile = CompressedApplicationInputStreamTest.createTarFile();
        Map<String, String> parameters = Collections.singletonMap("name", "foo");
        HttpResponse response = createHandler().handle(post(outFile, postHeaders, parameters));
        assertNotNull(response);
        assertThat(response.getStatus(), is(OK));
        assertThat(SessionHandlerTest.getRenderedString(response),
                   is("{\"log\":[]" + tenantMessage + ",\"session-id\":\"0\",\"prepared\":\"http://" +
                              hostname + ":" + port + pathPrefix + "0/prepared\",\"content\":\"http://" +
                              hostname + ":" + port + pathPrefix + "0/content/\",\"message\":\"Session 0" + createdMessage + "}"));
    }

    @Test
    public void require_that_session_factory_is_called() throws IOException {
        File outFile = CompressedApplicationInputStreamTest.createTarFile();
        createHandler().handle(post(outFile));
        assertTrue(sessionFactory.createCalled);
    }

    @Test
    public void require_that_handler_does_not_support_get() throws IOException {
        HttpResponse response = createHandler().handle(HttpRequest.createTestRequest(pathPrefix, GET));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, METHOD_NOT_ALLOWED,
                                                            HttpErrorResponse.errorCodes.METHOD_NOT_ALLOWED,
                                                            "Method 'GET' is not supported");
    }

    @Test
    public void require_internal_error_when_exception() throws IOException {
        sessionFactory.doThrow = true;
        File outFile = CompressedApplicationInputStreamTest.createTarFile();
        HttpResponse response = createHandler().handle(post(outFile));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, INTERNAL_SERVER_ERROR,
                                                            HttpErrorResponse.errorCodes.INTERNAL_SERVER_ERROR,
                                                            "foo");
    }

    @Test
    public void require_that_handler_unpacks_application() throws IOException {
        File outFile = CompressedApplicationInputStreamTest.createTarFile();
        createHandler().handle(post(outFile));
        assertTrue(sessionFactory.createCalled);
        final File applicationPackage = sessionFactory.applicationPackage;
        assertNotNull(applicationPackage);
        assertTrue(applicationPackage.exists());
        final File[] files = applicationPackage.listFiles();
        assertNotNull(files);
        assertThat(files.length, is(3));
    }

    @Test
    public void require_that_session_is_stored_in_repo() throws IOException {
        File outFile = CompressedApplicationInputStreamTest.createTarFile();
        createHandler().handle(post(outFile));
        assertNotNull(localSessionRepo.getSession(0l));
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

    private SessionCreateHandler createHandler() {
        return new SessionCreateHandler(
                SessionCreateHandler.testOnlyContext(),
                new ApplicationRepository(tenantRepository,
                                          new SessionHandlerTest.MockProvisioner(),
                                          clock),
                tenantRepository,
                componentRegistry.getConfigserverConfig());

    }

    private HttpRequest post() throws FileNotFoundException {
        return post(null, postHeaders, new HashMap<>());
    }

    private HttpRequest post(File file) throws FileNotFoundException {
        return post(file, postHeaders, new HashMap<>());
    }

    private HttpRequest post(File file, Map<String, String> headers, Map<String, String> parameters) throws FileNotFoundException {
        HttpRequest request = HttpRequest.createTestRequest("http://" + hostname + ":" + port + "/application/v2/tenant/" + tenant + "/session",
                POST,
                file == null ? null : new FileInputStream(file),
                parameters);
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.getJDiscRequest().headers().put(entry.getKey(), entry.getValue());
        }
        return request;
    }

    private HttpRequest post(Map<String, String> parameters) throws FileNotFoundException {
        return post(null, new HashMap<>(), parameters);
    }
}
