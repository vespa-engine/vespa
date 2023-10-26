// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import ai.vespa.validation.StringWrapper;

import static ai.vespa.validation.Validation.requireLength;

/**
 * @author olaa
 */
public class TaxId extends StringWrapper<TaxId> {

    public TaxId(String value) {
        super(value);
        requireLength(value, "tax code length", 0, 64);
    }

    public static TaxId empty() {
        return new TaxId("");
    }
}
