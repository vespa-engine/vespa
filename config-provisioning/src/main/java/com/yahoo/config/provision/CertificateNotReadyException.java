// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

/**
 * Exception thrown when trying to validate an application which is configured
 * with a certificate that is not yet retrievable
 * 
 * @author andreer
 *
 */
public class CertificateNotReadyException extends TransientException {

    public CertificateNotReadyException(String message) {
        super(message);
    }

}
