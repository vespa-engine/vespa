// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2;

import com.google.common.io.Files;
import com.yahoo.config.model.application.provider.FilesApplicationPackage;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.http.HttpRequest;
import com.yahoo.text.Utf8;
import com.yahoo.vespa.config.server.ApplicationRepository;
import com.yahoo.vespa.config.server.TestComponentRegistry;
import com.yahoo.vespa.config.server.http.ContentHandlerTestBase;
import com.yahoo.vespa.config.server.http.SessionHandlerTest;
import com.yahoo.vespa.config.server.tenant.TenantBuilder;
import com.yahoo.vespa.config.server.tenant.TenantRepository;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author Ulf Lilleengen
 */
public class SessionContentHandlerTest extends ContentHandlerTestBase {
    private static final TenantName tenant = TenantName.from("contenttest");

    private final TestComponentRegistry componentRegistry = new TestComponentRegistry.Builder().build();
    private final Clock clock = componentRegistry.getClock();

    private TenantRepository tenantRepository;
    private SessionContentHandler handler = null;
    
    @Before
    public void setupHandler() throws Exception {
        tenantRepository = new TenantRepository(componentRegistry, false);
        tenantRepository.addTenant(TenantBuilder.create(componentRegistry, tenant));
        tenantRepository.getTenant(tenant).getLocalSessionRepo().addSession(new MockSession(1L, FilesApplicationPackage.fromFile(createTestApp())));
        handler = createHandler();
        pathPrefix = "/application/v2/tenant/" + tenant + "/session/";
        baseUrl = "http://foo:1337/application/v2/tenant/" + tenant + "/session/1/content/";
    }

    @Test
    public void require_that_directories_can_be_created() throws IOException {
        assertMkdir("/bar/");
        assertMkdir("/bar/brask/");
        assertMkdir("/bar/brask/");
        assertMkdir("/bar/brask/bram/");
        assertMkdir("/brask/og/bram/");
    }// TODO: Enable when we have a predictable way of checking request body existence.

    @Test
    @Ignore
    public void require_that_mkdir_with_body_is_illegal(){
        HttpResponse response = put("/foobio/", "foo");
        assertNotNull(response);
        assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST));
    }

    @Test
    public void require_that_nonexistant_session_returns_not_found() {
        HttpResponse response = doRequest(HttpRequest.Method.GET, "/test.txt", 2l);
        assertNotNull(response);
        assertThat(response.getStatus(), is(Response.Status.NOT_FOUND));
    }

    protected HttpResponse put(String path, String content) {
        ByteArrayInputStream data = new ByteArrayInputStream(Utf8.toBytes(content));
        return doRequest(HttpRequest.Method.PUT, path, data);
    }

    @Test
    public void require_that_file_write_without_body_is_illegal() {
        HttpResponse response = doRequest(HttpRequest.Method.PUT, "/foobio.txt");
        assertNotNull(response);
        assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST));
    }

    @Test
    public void require_that_files_can_be_written() throws IOException {
        assertWriteFile("/foo/minfil.txt", "Mycontent");
        assertWriteFile("/foo/minfil.txt", "Differentcontent");
    }

    @Test
    public void require_that_nonexistant_file_returs_not_found_when_deleted() throws IOException {
        assertDeleteFile(Response.Status.NOT_FOUND, "/test2.txt", "{\"error-code\":\"NOT_FOUND\",\"message\":\"Session 1 does not contain a file 'test2.txt'\"}");
    }

    @Test
    public void require_that_files_can_be_deleted() throws IOException {
        assertDeleteFile(Response.Status.OK, "/test.txt");
        assertDeleteFile(Response.Status.NOT_FOUND, "/test.txt", "{\"error-code\":\"NOT_FOUND\",\"message\":\"Session 1 does not contain a file 'test.txt'\"}");
        assertDeleteFile(Response.Status.BAD_REQUEST, "/newtest", "{\"error-code\":\"BAD_REQUEST\",\"message\":\"File 'newtest' is not an empty directory\"}");
        assertDeleteFile(Response.Status.OK, "/newtest/testfile.txt");
        assertDeleteFile(Response.Status.OK, "/newtest");
    }

    @Test
    public void require_that_status_is_given_for_new_files() throws IOException {
        assertStatus("/test.txt?return=status",
                     "{\"status\":\"new\",\"md5\":\"d3b07384d113edec49eaa6238ad5ff00\",\"name\":\"http://foo:1337" + pathPrefix + "1/content/test.txt\"}");
        assertWriteFile("/test.txt", "Mycontent");
        assertStatus("/test.txt?return=status",
                     "{\"status\":\"changed\",\"md5\":\"01eabd73c69d78d0009ec93cd62d7f77\",\"name\":\"http://foo:1337" + pathPrefix + "1/content/test.txt\"}");
    }

    private void assertWriteFile(String path, String content) throws IOException {
        HttpResponse response = put(path, content);
        assertNotNull(response);
        assertThat(response.getStatus(), is(Response.Status.OK));
        assertContent(path, content);
        assertThat(SessionHandlerTest.getRenderedString(response),
                   is("{\"prepared\":\"http://foo:1337" + pathPrefix + "1/prepared\"}"));
    }

    private void assertDeleteFile(int statusCode, String filePath) throws IOException {
        assertDeleteFile(statusCode, filePath, "{\"prepared\":\"http://foo:1337" + pathPrefix + "1/prepared\"}");
    }

    private void assertDeleteFile(int statusCode, String filePath, String expectedResponse) throws IOException {
        HttpResponse response = doRequest(HttpRequest.Method.DELETE, filePath);
        assertNotNull(response);
        assertThat(response.getStatus(), is(statusCode));
        assertThat(SessionHandlerTest.getRenderedString(response), is(expectedResponse));
    }

    private void assertMkdir(String path) throws IOException {
        HttpResponse response = doRequest(HttpRequest.Method.PUT, path);
        assertNotNull(response);
        assertThat(response.getStatus(), is(Response.Status.OK));
        assertThat(SessionHandlerTest.getRenderedString(response),
                   is("{\"prepared\":\"http://foo:1337" + pathPrefix + "1/prepared\"}"));
    }

    private File createTestApp() throws IOException {
        File testApp = Files.createTempDir();
        FileUtils.copyDirectory(new File("src/test/apps/content"), testApp);
        return testApp;
    }

    protected HttpResponse doRequest(HttpRequest.Method method, String path) {
        return doRequest(method, path, 1l);
    }

    private HttpResponse doRequest(HttpRequest.Method method, String path, long sessionId) {
        return handler.handle(SessionHandlerTest.createTestRequest(pathPrefix, method, Cmd.CONTENT, sessionId, path));
    }

    private HttpResponse doRequest(HttpRequest.Method method, String path, InputStream data) {
        return doRequest(method, path, 1l, data);
    }

    private HttpResponse doRequest(HttpRequest.Method method, String path, long sessionId, InputStream data) {
        return handler.handle(SessionHandlerTest.createTestRequest(pathPrefix, method, Cmd.CONTENT, sessionId, path, data));
    }

    private SessionContentHandler createHandler() {
        return new SessionContentHandler(
                SessionContentHandler.testOnlyContext(),
                new ApplicationRepository(tenantRepository, new SessionHandlerTest.MockProvisioner(), clock),
                tenantRepository);
    }
}
