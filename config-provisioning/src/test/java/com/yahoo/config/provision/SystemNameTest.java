// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author hakonhall
 */
public class SystemNameTest {
    @Test
    public void test() {
        for (SystemName name : SystemName.values()) {
            assertEquals(name, SystemName.from(name.value()));
        }
    }
}