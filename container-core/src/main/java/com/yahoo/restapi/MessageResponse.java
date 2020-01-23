// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.yahoo.slime.Slime;

/**
 * A 200 ok response with a message in JSON.
 *
 * @author bratseth
 */
public class MessageResponse extends SlimeJsonResponse {

    public MessageResponse(String message) {
        super(slime(message));
    }

    private static Slime slime(String message) {
        var slime = new Slime();
        slime.setObject().setString("message", message);
        return slime;
    }

}
