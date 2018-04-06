// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yahoo.jdisc.Response;
import com.yahoo.jdisc.handler.FastContentWriter;
import com.yahoo.jdisc.handler.ResponseDispatch;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.http.HttpResponse;
import com.yahoo.jdisc.http.filter.DiscFilterRequest;
import com.yahoo.jdisc.http.filter.SecurityRequestFilter;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
import com.yahoo.yolean.chain.Before;

/**
 * @author bjorncs
 */
@Before("ControllerAuthorizationFilter")
public class AthenzFilterMock implements SecurityRequestFilter {

    public static final String IDENTITY_HEADER_NAME = "Athenz-Identity";
    public static final String ATHENZ_NTOKEN_HEADER_NAME = "Athenz-NToken";

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void filter(DiscFilterRequest request, ResponseHandler handler) {
        if (request.getMethod().equalsIgnoreCase("OPTIONS")) return;
        String identityName = request.getHeader(IDENTITY_HEADER_NAME);
        String nToken = request.getHeader(ATHENZ_NTOKEN_HEADER_NAME);
        if (identityName == null) {
            Response response = new Response(HttpResponse.Status.UNAUTHORIZED);
            response.headers().put("Content-Type", "application/json");
            ObjectNode errorMessage = mapper.createObjectNode();
            errorMessage.put("message", "Not authenticated");
            try (FastContentWriter writer = ResponseDispatch.newInstance(response).connectFastWriter(handler)) {
                writer.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(errorMessage));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        } else {
            AthenzIdentity identity = AthenzIdentities.from(identityName);
            AthenzPrincipal principal =
                    nToken == null ?
                            new AthenzPrincipal(identity) :
                            new AthenzPrincipal(identity, new NToken(nToken));
            request.setUserPrincipal(principal);
        }
    }

}
