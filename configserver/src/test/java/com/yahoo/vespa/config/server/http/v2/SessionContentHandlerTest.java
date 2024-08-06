// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.application.OrchestratorMock;
import com.yahoo.vespa.config.server.http.ContentHandlerTestBase;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.session.PrepareParams;
import com.yahoo.vespa.config.server.tenant.Tenant;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import com.yahoo.vespa.config.server.tenant.TestTenantRepository;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Clock;

import static com.yahoo.jdisc.Response.Status.BAD_REQUEST;
import static com.yahoo.jdisc.Response.Status.NOT_FOUND;
import static com.yahoo.jdisc.Response.Status.OK;
import static com.yahoo.jdisc.http.HttpRequest.Method.GET;
import static com.yahoo.jdisc.http.HttpRequest.Method.PUT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Ulf Lilleengen
 */
public class SessionContentHandlerTest extends ContentHandlerTestBase {
    private static final TenantName tenantName = TenantName.from("contenttest");
    private static final File testApp = new File("src/test/apps/content");

    private TenantRepository tenantRepository;
    private SessionContentHandler handler;
    private long sessionId;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Before
    public void setupHandler() throws IOException {
        ConfigserverConfig configserverConfig = new ConfigserverConfig.Builder()
                .configServerDBDir(temporaryFolder.newFolder("serverdb").getAbsolutePath())
                .configDefinitionsDir(temporaryFolder.newFolder("configdefinitions").getAbsolutePath())
                .fileReferencesDir(temporaryFolder.newFolder().getAbsolutePath())
                .build();

        tenantRepository = new TestTenantRepository.Builder()
                .withConfigserverConfig(configserverConfig)
                .build();
        tenantRepository.addTenant(tenantName);

        ApplicationRepository applicationRepository = new ApplicationRepository.Builder()
                .withTenantRepository(tenantRepository)
                .withOrchestrator(new OrchestratorMock())
                .withConfigserverConfig(configserverConfig)
                .build();
        applicationRepository.deploy(testApp, new PrepareParams.Builder().applicationId(applicationId()).build());
        Tenant tenant = applicationRepository.getTenant(applicationId());
        sessionId = applicationRepository.getActiveLocalSession(tenant, applicationId()).get().getSessionId();

        handler = createHandler();
        pathPrefix = "/application/v2/tenant/" + tenantName + "/session/";
        baseUrl = "http://foo:1337/application/v2/tenant/" + tenantName + "/session/" + sessionId + "/content/";
    }

    @Test
    public void require_that_directories_can_be_created() throws IOException {
        assertMkdir("/bar/");
        assertMkdir("/bar/brask/");
        assertMkdir("/bar/brask/");
        assertMkdir("/bar/brask/bram/");
        assertMkdir("/brask/og/bram/");
    }

    @Test
    @Ignore
    // TODO: Enable when we have a predictable way of checking request body existence.
    public void require_that_mkdir_with_body_is_illegal(){
        HttpResponse response = put("/foobio/", "foo");
        assertNotNull(response);
        assertEquals(BAD_REQUEST, response.getStatus());
    }

    @Test
    public void require_that_nonexistent_session_returns_not_found() {
        HttpResponse response = doRequest(GET, "/test.txt", 9999);
        assertNotNull(response);
        assertEquals(NOT_FOUND, response.getStatus());
    }

    protected HttpResponse put(String path, String content) {
        return put(path, Utf8.toBytes(content));
    }

    protected HttpResponse put(String path, byte[] content) {
        return doRequest(PUT, path, sessionId, new ByteArrayInputStream(content));
    }

    @Test
    public void require_that_file_write_without_body_is_illegal() {
        HttpResponse response = doRequest(PUT, "/foobio.txt");
        assertNotNull(response);
        assertEquals(BAD_REQUEST, response.getStatus());
    }

    @Test
    public void require_that_files_can_be_written() throws IOException {
        assertWriteFile("/foo/minfil.txt", "Mycontent");
        assertWriteFile("/foo/minfil.txt", "Differentcontent");
        assertWriteBinaryFile("/foo/bar/test.jar",
                              Files.readAllBytes(new File("src/test/apps/content/foo/bar/test.jar").toPath()),
                              "56f62ad750881d2f8276136896ff84fb",
                              "application/java-archive");
    }

    @Test
    public void require_that_nonexistent_file_returns_not_found_when_deleted() throws IOException {
        assertDeleteFile(NOT_FOUND, "/test2.txt", "{\"error-code\":\"NOT_FOUND\",\"message\":\"Session " + sessionId + " does not contain a file at path '/test2.txt'\"}");
    }

    @Test
    public void require_that_files_can_be_deleted() throws IOException {
        assertDeleteFile(OK, "/test.txt");
        assertDeleteFile(NOT_FOUND, "/test.txt", "{\"error-code\":\"NOT_FOUND\",\"message\":\"Session "  + sessionId + " does not contain a file at path '/test.txt'\"}");
        assertDeleteFile(BAD_REQUEST, "/newtest", "{\"error-code\":\"BAD_REQUEST\",\"message\":\"File 'newtest' is not an empty directory\"}");
        assertDeleteFile(OK, "/newtest/testfile.txt");
        assertDeleteFile(OK, "/newtest");
    }

    @Test
    public void require_that_status_is_given_for_new_files() throws IOException {
        assertStatus("/test.txt?return=status",
                     "{\"status\":\"new\",\"md5\":\"d3b07384d113edec49eaa6238ad5ff00\",\"name\":\"http://foo:1337" + pathPrefix + sessionId + "/content/test.txt\"}");
        assertWriteFile("/test.txt", "Mycontent");
        assertStatus("/test.txt?return=status",
                     "{\"status\":\"changed\",\"md5\":\"01eabd73c69d78d0009ec93cd62d7f77\",\"name\":\"http://foo:1337" + pathPrefix + sessionId + "/content/test.txt\"}");
    }

    private void assertWriteFile(String path, String content) throws IOException {
        HttpResponse response = put(path, content);
        assertNotNull(response);
        assertEquals(OK, response.getStatus());
        assertContent(path, content);
        assertEquals("{\"prepared\":\"http://foo:1337" + pathPrefix + sessionId + "/prepared\"}",
                     getRenderedString(response));
    }

    private void assertWriteBinaryFile(String path, byte[] content, String expectedMd5, String expectedContentType) throws IOException {
        HttpResponse response = put(path, content);
        assertNotNull(response);
        assertEquals(OK, response.getStatus());
        assertBinaryContent(path, expectedMd5, expectedContentType);
    }

    private void assertDeleteFile(int statusCode, String filePath) throws IOException {
        assertDeleteFile(statusCode, filePath, "{\"prepared\":\"http://foo:1337" + pathPrefix + sessionId +  "/prepared\"}");
    }

    private void assertDeleteFile(int statusCode, String filePath, String expectedResponse) throws IOException {
        HttpResponse response = doRequest(HttpRequest.Method.DELETE, filePath);
        assertNotNull(response);
        assertEquals(statusCode, response.getStatus());
        assertEquals(expectedResponse, getRenderedString(response));
    }

    private void assertMkdir(String path) throws IOException {
        HttpResponse response = doRequest(PUT, path);
        assertNotNull(response);
        assertEquals(OK, response.getStatus());
        assertEquals("{\"prepared\":\"http://foo:1337" + pathPrefix + sessionId + "/prepared\"}",
                     getRenderedString(response));
    }

    protected HttpResponse doRequest(HttpRequest.Method method, String path) {
        return doRequest(method, path, sessionId);
    }

    private HttpResponse doRequest(HttpRequest.Method method, String path, long sessionId) {
        return handler.handle(SessionHandlerTest.createTestRequest(pathPrefix, method, Cmd.CONTENT, sessionId, path));
    }

    private HttpResponse doRequest(HttpRequest.Method method, String path, long sessionId, InputStream data) {
        return handler.handle(SessionHandlerTest.createTestRequest(pathPrefix, method, Cmd.CONTENT, sessionId, path, data));
    }

    private SessionContentHandler createHandler() {
        return new SessionContentHandler(
                SessionContentHandler.testContext(),
                new ApplicationRepository.Builder()
                        .withTenantRepository(tenantRepository)
                        .withOrchestrator(new OrchestratorMock())
                        .withClock(Clock.systemUTC())
                        .build()
        );
    }

    private ApplicationId applicationId() {
        return ApplicationId.from(tenantName, ApplicationName.defaultName(), InstanceName.defaultName());
    }

}
