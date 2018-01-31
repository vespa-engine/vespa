// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core;

import com.google.common.annotations.Beta;

/**
 * The request was not processed properly on the server.
 *
 * @author Einar M R Rosenvinge
 */
@SuppressWarnings("serial")
@Beta
public class ServerResponseException extends Exception {

    private final int responseCode;
    private final String responseString;

    public ServerResponseException(int responseCode, String responseString) {
        super(responseString);
        this.responseCode = responseCode;
        this.responseString = responseString;
    }

    public ServerResponseException(String responseString) {
        super(responseString);
        this.responseCode = 0;
        this.responseString = responseString;
    }

    @Override
    public String toString() {
        if (responseCode > 0) {
            return responseCode + ": " + responseString;
        }
        return responseString;
    }

}

