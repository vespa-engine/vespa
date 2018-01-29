// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.provider;

import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class DebugHandlerHelperTest {
    @Test
    public void trivial() {
        DebugHandlerHelper helper = new DebugHandlerHelper();
        helper.addConstant("constant-key", "constant-value");

        NodeAdminDebugHandler handler = new NodeAdminDebugHandler() {
            @Override
            public Map<String, Object> getDebugPage() {
                return Collections.singletonMap("handler-value-key", "handler-value-value");
            }
        };
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