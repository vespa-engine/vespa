// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.bcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BcpStatus {
    public String rotationStatus;
    public String reason;

    // For jackson
    public BcpStatus() {}

    public BcpStatus(String rotationStatus, String reason) {
        this.rotationStatus = rotationStatus;
        this.reason = reason;
    }
}
