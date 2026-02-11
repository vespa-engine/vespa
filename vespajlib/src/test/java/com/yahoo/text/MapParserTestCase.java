// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

/**
 * @author bratseth
 */
public class MapParserTestCase {

    private static final double delta=0.0001;

    @Test
    public void testEmpty() {
        assertEquals(0, new DoubleMapParser().parseToMap("{}").size());
    }

    @Test
    public void testPlain() {
        Map<String,Double> values=new DoubleMapParser().parseToMap("{a:0.33,foo:-1.13,bar:1}");
        assertEquals(3, values.size());
        assertEquals(0.33d,values.get("a"),delta);
        assertEquals(-1.13d,values.get("foo"),delta);
        assertEquals(1.0d,values.get("bar"),delta);
    }

    @Test
    public void testNoisy() {
        Map<String,Double> values=new DoubleMapParser().parseToMap("  { a:0.33, foo:-1.13,bar:1,\"key:colon,\":1.2, '}':0}");
        assertEquals(5, values.size());
        assertEquals(0.33d,values.get("a"),delta);
        assertEquals(-1.13d,values.get("foo"),delta);
        assertEquals(1.0d,values.get("bar"),delta);
        assertEquals(1.2,values.get("key:colon,"),delta);
        assertEquals(0,values.get("}"),delta);
    }

    @Test
    public void testInvalid() {
        assertException("Missing quoted string termination","Expected a string terminated by '\"' starting at position 9 but was 'f'","{a:0.33,\"foo:1,bar:1}");
        assertException("Missing map termination","Expected a value followed by ',' or '}' starting at position 10 but was '1'","{a:0.33,b:1");
        assertException("Missing map start","Expected '{' starting at position 0 but was 'a'","a:0.33,b:1}");
        assertException("Missing comma separator","Expected a legal value from position 3 to 11 but was '0.33 b:1'","{a:0.33 b:1}");
        assertException("A single key with no value","Expected a key followed by ':' starting at position 1 but was 'f'","{foo}");
        assertException("A key with no value","Expected ':' starting at position 4 but was ','","{foo,a:2}");
        assertException("Invalid value","Expected a legal value from position 9 to 19 but was 'notanumber'","{invalid:notanumber}");
        assertException("Double key","Expected a legal value from position 3 to 6 but was 'a:1'","{a:a:1}");
    }

    private void assertException(String explanation,String exceptionString,String invalidMapString) {
        try {
            Map<String,Double> map=new DoubleMapParser().parseToMap(invalidMapString);
            fail("Expected exception on: " + explanation + " but parsed to " + map);
        }
        catch (IllegalArgumentException e) {
            assertEquals("Expected message on: " + explanation,exceptionString,e.getCause().getMessage());
        }
    }

    public static final class DoubleMapParser extends MapParser<Double> {

        @Override
        protected Double parseValue(String value) {
            return Double.parseDouble(value);
        }

    }

}
