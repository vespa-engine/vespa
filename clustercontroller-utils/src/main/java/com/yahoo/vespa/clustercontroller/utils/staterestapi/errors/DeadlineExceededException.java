// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.utils.staterestapi.errors;

public class DeadlineExceededException extends StateRestApiException {
    public DeadlineExceededException(String description) {
        super(description);
    }
}
