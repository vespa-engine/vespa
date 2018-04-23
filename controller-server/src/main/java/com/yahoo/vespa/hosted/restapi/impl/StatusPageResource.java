// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.restapi.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.yahoo.container.jaxrs.annotation.Component;
import com.yahoo.container.jdisc.SecretStore;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriBuilder;

/**
 * Proxies requests from controller to https://xxx.statuspage.io/api/v2/yyy.json?api_key=zzz[&amp;since=YYYY-MM-DDThh:mm[:ss]±hh:mm]
 *
 * @author andreer
 */
@Path("/v1/")
@Produces(MediaType.APPLICATION_JSON)
public class StatusPageResource implements com.yahoo.vespa.hosted.controller.api.statuspage.StatusPageResource {

    private final Client client;
    private final SecretStore secretStore;

    @Inject
    public StatusPageResource(@Component SecretStore secretStore) {
        this(secretStore, ClientBuilder.newClient());
    }

    protected StatusPageResource(SecretStore secretStore, Client client) {
        this.secretStore = secretStore;
        this.client = client;
    }

    protected UriBuilder statusPageURL(String page, String since) {
        String[] secrets = secretStore.getSecret("vespa_hosted.controller.statuspage_api_key").split(":");
        UriBuilder uriBuilder = UriBuilder.fromUri("https://" + secrets[0] + ".statuspage.io/api/v2/" + page + ".json?api_key=" + secrets[1]);
        if (since != null) {
            uriBuilder.queryParam("since", since);
        }

        return uriBuilder;
    }

    @Override
    public JsonNode statusPage(String page, String since) {
        WebTarget target = client.target(statusPageURL(page, since));
        return target.request().get(JsonNode.class);
    }

}
