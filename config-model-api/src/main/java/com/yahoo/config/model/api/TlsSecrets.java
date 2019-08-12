// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.api;

public class TlsSecrets {
    public static final TlsSecrets MISSING = new TlsSecrets();

    private final String certificate;
    private final String key;

    private TlsSecrets() {
        this(null, null);
    }

    public TlsSecrets(String certificate, String key) {
        this.certificate = certificate;
        this.key = key;
    }

    public String certificate() {
        return certificate;
    }

    public String key() {
        return key;
    }

    public boolean isMissing() {
        return this == MISSING;
    }
}
