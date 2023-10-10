// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.errors;

public class UnknownMasterException extends NotMasterException {

    public UnknownMasterException(String message) {
        super(message);
    }

    public UnknownMasterException() {
        super("No known master cluster controller currently exists.");
    }

}
