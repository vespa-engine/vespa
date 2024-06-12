// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

import com.yahoo.config.provision.TransientException;

/**
 * Thrown by {@link HostProvisioner} to indicate that (de)provisioning of a host has
 * failed due request being throttled by the cloud provider.
 *
 * @author freva
 */
public class ThrottleProvisioningException extends TransientException {
    public ThrottleProvisioningException(String message) {
        super(message);
    }
}
