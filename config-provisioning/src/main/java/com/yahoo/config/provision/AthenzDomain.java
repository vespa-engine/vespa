// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * @author mortent
 */
public class AthenzDomain extends PatternedStringWrapper<AthenzDomain> {

    private static final Pattern PATTERN = Pattern.compile("[a-zA-Z0-9_][a-zA-Z0-9_.-]*[a-zA-Z0-9_]");

    private AthenzDomain(String name) {
        super(name, PATTERN, "Athenz domain");
    }

    public static AthenzDomain from(String value) {
        return new AthenzDomain(value);
    }

}
