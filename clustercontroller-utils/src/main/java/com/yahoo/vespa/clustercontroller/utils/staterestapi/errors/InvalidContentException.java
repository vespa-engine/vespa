// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.errors;

public class InvalidContentException extends StateRestApiException {

    public InvalidContentException(String description) {
        super(description, 400, "Content of HTTP request had invalid data");
    }

}
