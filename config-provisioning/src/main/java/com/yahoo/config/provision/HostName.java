// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.http.DomainName;

import static ai.vespa.validation.Validation.require;
import static ai.vespa.validation.Validation.requireLength;

/**
 * Hostnames match {@link #domainNamePattern}, and are restricted to 64 characters in length.
 *
 * @author jonmv
 */
public class HostName extends DomainName {

    private HostName(String value) {
        super(requireLength(require( ! value.endsWith("."),
                                     value, "hostname cannot end with '.'"),
                            "hostname length", 1, 64),
              "hostname");
    }

    public static HostName of(String value) {
        return new HostName(value);
    }

}
