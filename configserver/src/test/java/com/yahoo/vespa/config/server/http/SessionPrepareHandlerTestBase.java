// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.provision.TenantName;
import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.path.Path;
import com.yahoo.vespa.config.server.*;
import com.yahoo.vespa.config.server.session.*;

import com.yahoo.vespa.curator.Curator;
import org.junit.Test;

import java.io.IOException;

import static com.yahoo.jdisc.http.HttpRequest.Method;
import static com.yahoo.jdisc.http.HttpResponse.Status.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

/**
 * @author hmusum
 * @since 5.1.14
 */
public abstract class SessionPrepareHandlerTestBase extends SessionHandlerTest {

    protected Curator curator;
    private SessionZooKeeperClient zooKeeperClient;
    protected LocalSessionRepo localRepo;

    protected String preparedMessage = " prepared.\"}";
    protected String tenantMessage = "";


    @Test
    public void require_error_when_session_id_does_not_exist() throws Exception {
        // No session with this id exists
        HttpResponse response = createHandler().handle(SessionHandlerTest.createTestRequest(pathPrefix, Method.PUT, Cmd.PREPARED, 9999L));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, NOT_FOUND, HttpErrorResponse.errorCodes.NOT_FOUND, "Session 9999 was not found");
    }

    @Test
    public void require_error_when_session_id_not_a_number() throws Exception {
        final String session = "notanumber/prepared";
        HttpResponse response = createHandler().handle(SessionHandlerTest.createTestRequest(pathPrefix + session));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, BAD_REQUEST,
                HttpErrorResponse.errorCodes.BAD_REQUEST,
                "Session id in request is not a number, request was 'http://" + hostname + ":" + port + pathPrefix + session + "'");
    }

    @Test
    public void require_that_handler_gives_error_for_unsupported_methods() throws Exception {
        testUnsupportedMethod(SessionHandlerTest.createTestRequest(pathPrefix, Method.POST, Cmd.PREPARED, 1L));
        testUnsupportedMethod(SessionHandlerTest.createTestRequest(pathPrefix, Method.DELETE, Cmd.PREPARED, 1L));
    }

    protected void testUnsupportedMethod(HttpRequest request) throws Exception {
        HttpResponse response = createHandler().handle(request);
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(response, METHOD_NOT_ALLOWED,
                HttpErrorResponse.errorCodes.METHOD_NOT_ALLOWED,
                "Method '" + request.getMethod().name() + "' is not supported");
    }

    @Test
    public void require_that_activate_url_is_returned_on_success() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        HttpResponse response = createHandler().handle(SessionHandlerTest.createTestRequest(pathPrefix, Method.PUT, Cmd.PREPARED, 1L));
        assertThat(session.getStatus(), is(Session.Status.PREPARE));
        assertNotNull(response);
        assertThat(response.getStatus(), is(OK));
        assertResponseContains(response, "\"activate\":\"http://foo:1337" + pathPrefix + "1/active\",\"message\":\"Session 1" + preparedMessage);
    }

    @Test
    public void require_debug() throws Exception {
        HttpResponse response = createHandler().handle(SessionHandlerTest.createTestRequest(pathPrefix, Method.PUT, Cmd.PREPARED, 9999L, "?debug=true"));
        assertThat(response.getStatus(), is(NOT_FOUND));
        assertThat(SessionHandlerTest.getRenderedString(response), containsString("NotFoundException"));
    }

    @Test
    public void require_verbose() throws Exception {
        MockSession session = new MockSession(1, null);
        session.doVerboseLogging = true;
        localRepo.addSession(session);
        HttpResponse response = createHandler().handle(SessionHandlerTest.createTestRequest(pathPrefix, Method.PUT, Cmd.PREPARED, 1L, "?verbose=true"));
        assertThat(response.getStatus(), is(OK));
        assertThat(SessionHandlerTest.getRenderedString(response), containsString("debuglog"));
    }

    /**
     * A mock remote session repo based on contents of local repo
     */
    private RemoteSessionRepo fromLocalSessionRepo(LocalSessionRepo localRepo) {
        RemoteSessionRepo remoteRepo = new RemoteSessionRepo();
        PathProvider pathProvider = new PathProvider(Path.createRoot());
        for (LocalSession ls : localRepo.listSessions()) {

            zooKeeperClient = new MockSessionZKClient(curator, pathProvider.getSessionDirs().append(String.valueOf(ls.getSessionId())));
            if (ls.getStatus()!=null) zooKeeperClient.writeStatus(ls.getStatus());
            RemoteSession remSess = new RemoteSession(TenantName.from("default"), ls.getSessionId(),
                    new TestComponentRegistry.Builder().curator(curator).build(),
                    zooKeeperClient);
            remoteRepo.addSession(remSess);
        }
        return remoteRepo;
    }

    @Test
    public void require_get_response_activate_url_on_ok() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        SessionHandler sessHandler = createHandler(fromLocalSessionRepo(localRepo));
        sessHandler.handle(SessionHandlerTest.createTestRequest(pathPrefix, Method.PUT, Cmd.PREPARED, 1L));
        session.setStatus(Session.Status.PREPARE);
        zooKeeperClient.writeStatus(Session.Status.PREPARE);
        HttpResponse getResponse = sessHandler.handle(SessionHandlerTest.createTestRequest(pathPrefix, Method.GET, Cmd.PREPARED, 1L));
        assertResponseContains(getResponse, "\"activate\":\"http://foo:1337" + pathPrefix + "1/active\",\"message\":\"Session 1" + preparedMessage);
    }

    @Test
    public void require_get_response_error_on_not_prepared() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        SessionHandler sessHandler = createHandler(fromLocalSessionRepo(localRepo));
        session.setStatus(Session.Status.NEW);
        zooKeeperClient.writeStatus(Session.Status.NEW);
        HttpResponse getResponse = sessHandler.handle(SessionHandlerTest.createTestRequest(pathPrefix, Method.GET, Cmd.PREPARED, 1L));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(getResponse, BAD_REQUEST,
                HttpErrorResponse.errorCodes.BAD_REQUEST,
                "Session not prepared: 1");
        session.setStatus(Session.Status.ACTIVATE);
        zooKeeperClient.writeStatus(Session.Status.ACTIVATE);
        getResponse = sessHandler.handle(SessionHandlerTest.createTestRequest(pathPrefix, Method.GET, Cmd.PREPARED, 1L));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(getResponse, BAD_REQUEST,
                HttpErrorResponse.errorCodes.BAD_REQUEST,
                "Session is active: 1");
    }

    @Test
    public void require_cannot_prepare_active_session() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        session.setStatus(Session.Status.ACTIVATE);
        SessionHandler sessionHandler = createHandler(fromLocalSessionRepo(localRepo));
        HttpResponse putResponse = sessionHandler.handle(SessionHandlerTest.createTestRequest(pathPrefix, Method.PUT, Cmd.PREPARED, 1L));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(putResponse, BAD_REQUEST,
                HttpErrorResponse.errorCodes.BAD_REQUEST,
                "Session is active: 1");
    }

    @Test
    public void require_get_response_error_when_session_id_does_not_exist() throws Exception {
        MockSession session = new MockSession(1, null);
        localRepo.addSession(session);
        SessionHandler sessHandler = createHandler(fromLocalSessionRepo(localRepo));
        HttpResponse getResponse = sessHandler.handle(SessionHandlerTest.createTestRequest(pathPrefix, Method.GET, Cmd.PREPARED, 9999L));
        HandlerTest.assertHttpStatusCodeErrorCodeAndMessage(getResponse, NOT_FOUND,
                HttpErrorResponse.errorCodes.NOT_FOUND,
                "Session 9999 was not found");
    }

    protected static void assertResponse(HttpResponse response, String activateString) throws IOException {
        // TODO Test when more logging is added
        //assertThat(baos.toString(), startsWith("{\"log\":[{\"time\":"));
        assertThat(SessionHandlerTest.getRenderedString(response), endsWith(activateString));
    }

    protected static void assertResponseContains(HttpResponse response, String string) throws IOException {
        assertThat(SessionHandlerTest.getRenderedString(response), containsString(string));
    }

    protected static void assertResponseNotContains(HttpResponse response, String string) throws IOException {
        assertThat(SessionHandlerTest.getRenderedString(response), not(containsString(string)));
    }

    public abstract SessionHandler createHandler();

    public abstract SessionHandler createHandler(RemoteSessionRepo remoteSessionRepo);
}
