// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.exception;

import com.yahoo.config.provision.TransientException;

/**
 * Transient exception thrown on behalf of a load balancer service
 *
 * @author mpolden
 */
public class LoadBalancerServiceException extends TransientException {

    public LoadBalancerServiceException(String message) {
        super(message);
    }

    public LoadBalancerServiceException(String message, Throwable cause) {
        super(message, cause);
    }

}
