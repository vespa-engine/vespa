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
