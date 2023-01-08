package com.yahoo.jdisc.application;

import org.junit.jupiter.api.Test;
import org.osgi.framework.Version;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gjoranv
 */
public class BsnVersionTest {

    @Test
    void readable_string_can_be_retrieved() {
        BsnVersion bsnVersion = new BsnVersion("com.yahoo.foo", new Version("1.0.0"));
        assertEquals("com.yahoo.foo version:1.0.0", bsnVersion.toReadableString());
    }

    @Test
    void version_qualifier_can_be_retrieved() {
        BsnVersion bsnVersion = new BsnVersion("foo", new Version(1, 2, 3, "SNAPSHOT"));
        assertEquals("SNAPSHOT", bsnVersion.version().getQualifier());
    }
}
