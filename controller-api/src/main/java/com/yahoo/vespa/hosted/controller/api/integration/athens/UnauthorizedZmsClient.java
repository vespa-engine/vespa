// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athens;

import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;

import java.util.List;

/**
 * @author gv
 */
public class UnauthorizedZmsClient {

    private final ZmsClient client;

    public UnauthorizedZmsClient(ZmsClientFactory zmsClientFactory) {
        client = zmsClientFactory.createClientWithoutPrincipal();
    }

    public List<AthensDomain> getDomainList(String prefix) {
        return client.getDomainList(prefix);
    }

}
