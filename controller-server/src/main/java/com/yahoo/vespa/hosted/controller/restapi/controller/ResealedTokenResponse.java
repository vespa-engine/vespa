// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.restapi.SlimeJsonResponse;
import com.yahoo.security.SharedKeyResealingSession;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Slime;

/**
 * A response that contains a decryption token "resealing response".
 *
 * @author vekterli
 */
public class ResealedTokenResponse extends SlimeJsonResponse {

    public ResealedTokenResponse(SharedKeyResealingSession.ResealingResponse response) {
        super(toSlime(response));
    }

    private static Slime toSlime(SharedKeyResealingSession.ResealingResponse response) {
        Slime slime = new Slime();
        Cursor root = slime.setObject();
        root.setString("resealResponse", response.toSerializedString());
        return slime;
    }

}
