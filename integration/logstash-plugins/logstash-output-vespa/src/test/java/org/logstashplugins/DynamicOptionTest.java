package org.logstashplugins;

import org.junit.Test;
import static org.junit.Assert.*;

public class DynamicOptionTest {

    @Test
    public void testStaticValue() {
        DynamicOption option = new DynamicOption("static-value");
        assertFalse(option.isDynamic());
        assertEquals("static-value", option.getParsedConfigValue());
    }

    @Test
    public void testDynamicValueWithBrackets() {
        DynamicOption option = new DynamicOption("%{[field_name]}");
        assertTrue(option.isDynamic());
        assertEquals("field_name", option.getParsedConfigValue());
    }

    @Test
    public void testDynamicValueWithoutBrackets() {
        DynamicOption option = new DynamicOption("%{field_name}");
        assertTrue(option.isDynamic());
        assertEquals("field_name", option.getParsedConfigValue());
    }

    @Test
    public void testEmptyValue() {
        DynamicOption option = new DynamicOption("");
        assertFalse(option.isDynamic());
        assertEquals("", option.getParsedConfigValue());
    }

    @Test
    public void testPartialMatch() {
        DynamicOption option = new DynamicOption("prefix_%{field_name");
        assertFalse(option.isDynamic());
        assertEquals("prefix_%{field_name", option.getParsedConfigValue());
    }
} 