// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

/**
 * @author freva
 */
public class FatalProvisioningException extends RuntimeException {
    public FatalProvisioningException(String message) {
        super(message);
    }

    public FatalProvisioningException(String message, Exception cause) {
        super(message, cause);
    }
}
