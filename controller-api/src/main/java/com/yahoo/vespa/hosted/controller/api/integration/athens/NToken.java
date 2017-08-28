// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athens;

import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;

import java.security.PublicKey;

/**
 * @author mpolden
 */
public interface NToken {

    AthensPrincipal getPrincipal();
    UserId getUser();
    AthensDomain getDomain();
    String getToken();
    String getKeyId();
    void validateSignatureAndExpiration(PublicKey publicKey) throws InvalidTokenException;

}
