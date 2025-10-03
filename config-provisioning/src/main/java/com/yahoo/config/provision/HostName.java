// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.http.DomainName;

import static ai.vespa.validation.Validation.require;
import static ai.vespa.validation.Validation.requireLength;

/**
 * Fully qualified (FQDN) hostnames are DNS names ({@link #domainNamePattern}).
 *
 * <p>To allow long FQDN hostnames, the Linux kernel hostname should be set to the leaf label only
 * (because it has a limitation of 64 characters), and system and DNS should be configured to resolve the FQDN hostname.</p>
 *
 * @author Jon Marius Venstad
 */
public class HostName extends DomainName {

    private HostName(String value) {
        super(require( ! value.endsWith("."), value, "hostname cannot end with '.'"), "hostname");
    }

    public static HostName of(String value) {
        return new HostName(value);
    }

}
