// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * <p>Superclasses of parsers of a map represented textually as
 * <code>{key1:value1,"anystringkey":value2,'anystringkey2':value3 ...}</code>.
 * This parser must be extended to specify how to handle the key/value pairs.</p>
 *
 * <p>Example: To create a Double map parser:</p>
 * <pre>
 * public static final class DoubleMapParser extends MapParser&lt;Double&gt; {
 *     private Map&lt;String, Double&gt; map;
 *
 *     ...
 *
 *     &#64;Override
 *     protected Double handleKeyValue(String key, String value) {
 *         map.put(key, Double.parseDouble(value));
 *     }
 *
 * }
 * </pre>
 *
 * <p>Map parsers are NOT multithread safe, but are cheap to construct.</p>
 *
 * @author bratseth
 */
public abstract class SimpleMapParser {

    private PositionedString s;

    /**
     * Parses a map on the form <code>{key1:value1,key2:value2 ...}</code>
     *
     * @param string the textual representation of the map
     */
    public void parse(String string) {
        try {
            this.s=new PositionedString(string);

            s.consumeSpaces();
            s.consume('{');
            while ( ! s.peek('}')) {
                s.consumeSpaces();
                String key=consumeKey();
                s.consume(':');
                s.consumeSpaces();
                consumeValue(key);
                s.consumeOptional(',');
                s.consumeSpaces();
            }
            s.consume('}');
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("'" + s + "' is not a legal sparse vector string",e);
        }
    }

    private String consumeKey() {
        if (s.consumeOptional('"')) {
            String key=s.consumeTo('"');
            s.consume('"');
            return key;
        }
        else if (s.consumeOptional('\'')) {
            String key=s.consumeTo('\'');
            s.consume('\'');
            return key;
        }
        else {
            int keyEnd=findEndOfKey();
            if (keyEnd<0)
                throw new IllegalArgumentException("Expected a key followed by ':' " + s.at());
            return s.consumeToPosition(keyEnd);
        }
    }

    protected int findEndOfKey() {
        for (int peekI=s.position(); peekI<s.string().length(); peekI++) {
            if (s.string().charAt(peekI)==':' || s.string().charAt(peekI)==',')
                return peekI;
        }
        return -1;
    }

    protected int findEndOfValue() {
        for (int peekI=s.position(); peekI<s.string().length(); peekI++) {
            if (s.string().charAt(peekI)==',' || s.string().charAt(peekI)=='}')
                return peekI;
        }
        return -1;
    }

    protected void consumeValue(String key) {
        // find the next comma or bracket, whichever is next
        int endOfValue=findEndOfValue();
        if (endOfValue<0) {
            throw new IllegalArgumentException("Expected a value followed by ',' or '}' " + s.at());
        }
        try {
            handleKeyValue(key, s.substring(endOfValue));
            s.setPosition(endOfValue);
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Expected a legal value from position " + s.position() + " to " + endOfValue +
                    " but was '" + s.substring(endOfValue) + "'", e);
        }
    }

    /** Returns the string being parsed along with its current position */
    public PositionedString string() { return s; }

    protected abstract void handleKeyValue(String key, String value);

}
