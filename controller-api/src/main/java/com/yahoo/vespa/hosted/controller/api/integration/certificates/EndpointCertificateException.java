// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

public class EndpointCertificateException extends RuntimeException {

    private final Type type;

    public EndpointCertificateException(Type type, String message) {
        super(message);
        this.type = type;
    }

    public EndpointCertificateException(Type type, String message, Throwable cause) {
        super(message, cause);
        this.type = type;
    }

    public Type type() {
        return type;
    }

    public enum Type {
        CERT_NOT_AVAILABLE,
        VERIFICATION_FAILURE
    }
}
