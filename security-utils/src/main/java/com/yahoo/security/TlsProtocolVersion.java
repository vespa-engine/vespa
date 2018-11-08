// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

/**
 * TLS protocol versions
 *
 * @author bjorncs
 */
public enum TlsProtocolVersion {
    TLS_1("TLSv1"),
    TLS_1_1("TLSv1.1"),
    TLS_1_2("TLSv1.2"),
    TLS_1_3("TLSv1.3");

    private final String protocolName;

    TlsProtocolVersion(String protocolName) {
        this.protocolName = protocolName;
    }

    public String getProtocolName() {
        return protocolName;
    }
}
