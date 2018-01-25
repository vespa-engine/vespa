// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.security;

/**
 * @author mpolden
 */
public class KeyServiceMock implements KeyService {

    @Override
    public String getSecret(String key) {
        return "fake-secret-for-" + key;
    }

}
