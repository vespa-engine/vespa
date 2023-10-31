// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.tenant;

import ai.vespa.validation.StringWrapper;

import static ai.vespa.validation.Validation.requireLength;

/**
 * @author olaa
 */
public class PurchaseOrder extends StringWrapper<PurchaseOrder> {

    public PurchaseOrder(String value) {
        super(value);
        requireLength(value, "purchase order length", 0, 64);
    }

    public static PurchaseOrder empty() {
        return new PurchaseOrder("");
    }
    public boolean isEmpty() { return value().isEmpty(); }
}
