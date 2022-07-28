// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.provider;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DebugHandlerHelperTest {
    @Test
    void trivial() {
        DebugHandlerHelper helper = new DebugHandlerHelper();
        helper.addConstant("constant-key", "constant-value");

        NodeAdminDebugHandler handler = () -> Map.of("handler-value-key", "handler-value-value");
        helper.addHandler("handler-key", handler);

        helper.addThreadSafeSupplier("supplier-key", () -> "supplier-value");

        assertEquals("{" +
                "supplier-key=supplier-value, " +
                "handler-key={handler-value-key=handler-value-value}, " +
                "constant-key=constant-value" +
                "}",
                helper.getDebugPage().toString());
    }
}