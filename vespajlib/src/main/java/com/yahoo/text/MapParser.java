// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>Superclasses of parsers of a map represented textually as
 * <code>{key1:value1,"anystringkey":value2,'anystringkey2':value3 ...}</code>.
 * This parser must be extended to override the way values are parsed and constructed.</p>
 *
 * <p>Example: To create a Double map parser:</p>
 * <pre>
 * public static final class DoubleMapParser extends MapParser&lt;Double&gt; {
 *
 *     &#64;Override
 *     protected Double parseValue(String value) {
 *         return Double.parseDouble(value);
 *     }
 *
 * }
 * </pre>
 *
 * <p>Map parsers are NOT multithread safe, but are cheap to construct.</p>
 *
 * @author bratseth
 */
public abstract class MapParser<VALUETYPE> extends SimpleMapParser {

    private Map<String, VALUETYPE> map;

    /**
     * Convenience method doing return parse(s,new HashMap&lt;String,VALUETYPE&gt;())
     */
    public Map<String,VALUETYPE> parseToMap(String s) {
        return parse(s,new HashMap<>());
    }

    /**
     * Parses a map on the form <code>{key1:value1,key2:value2 ...}</code>
     *
     * @param string the textual representation of the map
     * @param map the map to which the values will be added
     * @return the input map instance for convenience
     */
    public Map<String,VALUETYPE> parse(String string,Map<String,VALUETYPE> map) {
        this.map = map;
        parse(string);
        return this.map;
    }

    protected void handleKeyValue(String key, String value) {
        map.put(key, parseValue(value));
    }

    protected abstract VALUETYPE parseValue(String value);

}
