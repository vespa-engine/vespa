// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.ContentChannel;
import com.yahoo.jdisc.handler.ReadableContentChannel;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.AthenzUtils;
import com.yahoo.vespa.hosted.controller.athenz.InvalidTokenException;
import com.yahoo.vespa.hosted.controller.athenz.NToken;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.Objects;

import static com.yahoo.jdisc.Response.Status.UNAUTHORIZED;
import static java.util.stream.Collectors.joining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author bjorncs
 */
public class AthenzPrincipalFilterTest {

    private static final NToken NTOKEN = createDummyToken();
    private static final String ATHENZ_PRINCIPAL_HEADER = "Athenz-Principal-Auth";

    private NTokenValidator validator;
    private AthenzPrincipal principal;

    @Before
    public void before() {
        validator = mock(NTokenValidator.class);
        principal = AthenzUtils.createPrincipal(new UserId("bob"));
    }

    @Test
    public void valid_ntoken_is_accepted() throws Exception {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(NTOKEN.getToken());

        when(validator.validate(NTOKEN)).thenReturn(principal);

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER);
        filter.filter(request, new ResponseHandlerMock());

        verify(request).setUserPrincipal(principal);
    }

    @Test
    public void missing_token_is_unauthorized() throws Exception {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(null);

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER);
        filter.filter(request, responseHandler);

        assertThat(responseHandler.response, notNullValue());
        assertThat(responseHandler.response.getStatus(), equalTo(UNAUTHORIZED));
        assertThat(responseHandler.getResponseContent(), containsString("NToken is missing"));
    }

    @Test
    public void invalid_token_is_unauthorized() throws Exception {
        DiscFilterRequest request = mock(DiscFilterRequest.class);
        when(request.getHeader(ATHENZ_PRINCIPAL_HEADER)).thenReturn(NTOKEN.getToken());

        when(validator.validate(NTOKEN)).thenThrow(new InvalidTokenException("Invalid token"));

        ResponseHandlerMock responseHandler = new ResponseHandlerMock();

        AthenzPrincipalFilter filter = new AthenzPrincipalFilter(validator, Runnable::run, ATHENZ_PRINCIPAL_HEADER);
        filter.filter(request, responseHandler);

        assertThat(responseHandler.response, notNullValue());
        assertThat(responseHandler.response.getStatus(), equalTo(UNAUTHORIZED));
        assertThat(responseHandler.getResponseContent(), containsString("Invalid token"));
    }

    private static NToken createDummyToken() {
        return new NToken.Builder(
                "U1", AthenzUtils.createPrincipal(new UserId("bob")), AthenzTestUtils.generateRsaKeypair().getPrivate(), "0")
                .build();
    }

    private static class ResponseHandlerMock implements ResponseHandler {

        public Response response;
        public ReadableContentChannel contentChannel;

        @Override
        public ContentChannel handleResponse(Response r) {
            response = Objects.requireNonNull(r);
            contentChannel = new ReadableContentChannel();
            return contentChannel;
        }

        public String getResponseContent() {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(contentChannel.toStream()))) {
                return br.lines().collect(joining(System.lineSeparator()));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

}
