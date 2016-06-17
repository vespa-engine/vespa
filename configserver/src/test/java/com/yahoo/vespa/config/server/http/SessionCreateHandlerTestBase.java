// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.google.common.io.Files;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.io.IOUtils;
import com.yahoo.vespa.config.server.CompressedApplicationInputStreamTest;
import com.yahoo.vespa.config.server.TimeoutBudget;
import com.yahoo.vespa.config.server.application.ApplicationRepo;
import com.yahoo.vespa.config.server.session.LocalSession;
import com.yahoo.vespa.config.server.session.LocalSessionRepo;
import com.yahoo.vespa.config.server.session.SessionFactory;

import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.yahoo.jdisc.Response.Status.*;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

/**
 * Tests for session create handlers, to make it easier to have
 * similar tests for more than one version of the API.
 *
 * @author hmusum
 * @since 5.1.28
 */
public abstract class SessionCreateHandlerTestBase extends SessionHandlerTest {

    public static final HashMap<String, String> postHeaders = new HashMap<>();

    protected String pathPrefix = "/application/v2/session/";
    protected String createdMessage = " created.\"";
    protected String tenantMessage = "";

    public File testApp = new File("src/test/apps/app");
    public LocalSessionRepo localSessionRepo;
    public ApplicationRepo applicationRepo;

    static {
        postHeaders.put(SessionCreate.contentTypeHeader, SessionCreate.APPLICATION_X_GZIP);
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
        MockSessionFactory factory = new MockSessionFactory();
        createHandler(factory).handle(post(outFile, postHeaders, params));
        assertTrue(factory.createCalled);
        assertThat(factory.applicationName, is("ulfio"));
    }

    protected void assertFromParameter(String expected, String from) throws IOException {
        HttpRequest request = post(Collections.singletonMap("from", from));
        MockSessionFactory factory = new MockSessionFactory();
        factory.applicationPackage = testApp;
        HttpResponse response = createHandler(factory).handle(request);
        assertNotNull(response);
        assertThat(response.getStatus(), is(OK));
        assertTrue(factory.createFromCalled);
        assertThat(SessionHandlerTest.getRenderedString(response),
                is("{\"log\":[]" + tenantMessage + ",\"session-id\":\"" + expected + "\",\"prepared\":\"http://" + hostname + ":" + port + pathPrefix +
                        expected + "/prepared\",\"content\":\"http://" + hostname + ":" + port + pathPrefix +
                        expected + "/content/\",\"message\":\"Session " + expected + createdMessage + "}"));
    }

    protected void assertIllegalFromParameter(String fromValue) throws IOException {
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
        MockSessionFactory sessionFactory = new MockSessionFactory();
        File outFile = CompressedApplicationInputStreamTest.createTarFile();
        createHandler(sessionFactory).handle(post(outFile));
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
        MockSessionFactory factory = new MockSessionFactory();
        factory.doThrow = true;
        File outFile = CompressedApplicationInputStreamTest.createTarFile();
        HttpResponse response = createHandler(factory).handle(post(outFile));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, INTERNAL_SERVER_ERROR,
                HttpErrorResponse.errorCodes.INTERNAL_SERVER_ERROR,
                "foo");
    }

    @Test
    public void require_that_handler_unpacks_application() throws IOException {
        MockSessionFactory sessionFactory = new MockSessionFactory();
        File outFile = CompressedApplicationInputStreamTest.createTarFile();
        createHandler(sessionFactory).handle(post(outFile));
        assertTrue(sessionFactory.createCalled);
        final File applicationPackage = sessionFactory.applicationPackage;
        assertNotNull(applicationPackage);
        assertTrue(applicationPackage.exists());
        final File[] files = applicationPackage.listFiles();
        assertNotNull(files);
        assertThat(files.length, is(2));
    }

    @Test
    public void require_that_session_is_stored_in_repo() throws IOException {
        File outFile = CompressedApplicationInputStreamTest.createTarFile();
        createHandler(new MockSessionFactory()).handle(post(outFile));
        assertNotNull(localSessionRepo.getSession(0l));
    }

    public abstract SessionHandler createHandler();

    public abstract SessionHandler createHandler(SessionFactory sessionFactory);

    public abstract HttpRequest post() throws FileNotFoundException;

    public abstract HttpRequest post(File file) throws FileNotFoundException;

    public abstract HttpRequest post(File file, Map<String, String> headers, Map<String, String> parameters) throws FileNotFoundException;

    public abstract HttpRequest post(Map<String, String> parameters) throws FileNotFoundException;

    public static class MockSessionFactory implements SessionFactory {
        public boolean createCalled = false;
        public boolean createFromCalled = false;
        public boolean doThrow = false;
        public File applicationPackage;
        public String applicationName;

        @Override
        public LocalSession createSession(File applicationDirectory, String applicationName, DeployLogger logger, TimeoutBudget timeoutBudget) {
            createCalled = true;
            this.applicationName = applicationName;
            if (doThrow) {
                throw new RuntimeException("foo");
            }
            final File tempDir = Files.createTempDir();
            try {
                IOUtils.copyDirectory(applicationDirectory, tempDir);
            } catch (IOException e) {
                e.printStackTrace();
            }
            this.applicationPackage = tempDir;
            return new SessionHandlerTest.MockSession(0, FilesApplicationPackage.fromFile(applicationPackage));
        }

        @Override
        public LocalSession createSessionFromExisting(LocalSession existingSession, DeployLogger logger, TimeoutBudget timeoutBudget) {
            if (doThrow) {
                throw new RuntimeException("foo");
            }
            createFromCalled = true;
            return new SessionHandlerTest.MockSession(existingSession.getSessionId() + 1, FilesApplicationPackage.fromFile(applicationPackage));
        }
    }
}

