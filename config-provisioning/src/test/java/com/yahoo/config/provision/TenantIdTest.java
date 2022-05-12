package com.yahoo.config.provision;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author enygaard
 */
public class TenantIdTest {

    @Test
    public void test() {
        assertEquals("", TenantId.from("").value());
        assertEquals(26, TenantId.create().value().length());
    }
}