// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.provisioning;

/**
 * Thrown by {@link HostProvisioner} to indicate that the provisioning of a host has
 * irrecoverably failed. The host should be deprovisioned and (together with its children)
 * removed from the node-repository.
 *
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
