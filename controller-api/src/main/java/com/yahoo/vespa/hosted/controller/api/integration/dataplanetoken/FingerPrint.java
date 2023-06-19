// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.dataplanetoken;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * A fingerprint to be used in dataplane token apis
 */
public class FingerPrint extends PatternedStringWrapper<FingerPrint> {

    static final Pattern namePattern = Pattern.compile("([a-f0-9]{2}:)+[a-f0-9]{2}");

    private FingerPrint(String name) {
        super(name, namePattern, "fingerPrint");
    }

    public static FingerPrint of(String value) {
        return new FingerPrint(value);
    }

}
