// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.querytransform;

import com.yahoo.text.PositionedString;
import com.yahoo.text.SimpleMapParser;

import java.math.BigInteger;

/**
 * Parses an attribute string on the format <code>{attribute:value, ...}</code>
 * where <code>value</code>' is either a single value or a list of values
 * <code>[value1,value2,...]</code>, and each of the values can have an optional
 * bitmap specified <code>value:bitmap</code>. <code>bitmap</code> can be either
 * a 64-bit hex number <code>0x1234</code> or a list of bits <code>[0, 2, 43,
 * 22, ...]</code>.
 *
 * @author Magnar Nedland
 */
abstract class BooleanAttributeParser extends SimpleMapParser {

    private boolean isMap = true;

    @Override
    public void parse(String s) {
        if (s == null || s.length() == 0) return;
        super.parse(s);
        if (string().position() != string().string().length()) {
            throw new IllegalArgumentException("Expected end of string " + string().at());
        }
    }

    // Value ends at ',' or '}' for map, and at ',' or ']' for list.
    @Override
    protected int findEndOfValue() {
        if (isMap) {
            return findNextButSkipLists(new char[]{',','}'}, string().string(), string().position());
        }
        return findNextButSkipLists(new char[]{',',']'}, string().string(), string().position());
    }

    @Override
    protected void handleKeyValue(String attribute, String value) {
        // string() will point to the start of value.
        if (string().peek('[') && isMap) {
            // begin parsing MultiValueQueryTerm
            isMap = false;
            parseMultiValue(attribute);
            isMap = true;
        } else {
            handleAttribute(attribute, value);
        }
    }

    /**
     * Parses a list of values for a given attribute. When calling this
     * function, string() must point to the start of the list.
     */
    private void parseMultiValue(String attribute) {
        // string() will point to the start of value.
        string().consume('[');
        while (!string().peek(']')) {
            string().consumeSpaces();
            consumeValue(attribute);
            string().consumeOptional(',');
            string().consumeSpaces();
        }
    }

    /**
     * Handles one attribute, possibly with a subquery bitmap.
     * @param attribute Attribute name
     * @param value Either value, or value:bitmap, where bitmap is either a 64-bit hex number or a list of bits.
     */
    private void handleAttribute(String attribute, String value) {
        int pos = value.indexOf(':');
        if (pos != -1) {
            parseBitmap(attribute, value.substring(0, pos), value.substring(pos + 1));
        } else {
            addAttribute(attribute, value);
        }
    }

    // Parses a bitmap string that's either a list of bits or a hex number.
    private void parseBitmap(String attribute, String value, String bitmap) {
        if (bitmap.charAt(0) == '[') {
            parseBitmapList(attribute, value, bitmap);
        } else {
            parseBitmapHex(attribute, value, bitmap);
        }
    }

    /**
     * Adds attributes with the specified bitmap to normalizer.
     * @param attribute Attribute to add
     * @param value Value of attribute
     * @param bitmap Bitmap as a hex number, with a '0x' prefix.
     */
    private void parseBitmapHex(String attribute, String value, String bitmap) {
        PositionedString s = new PositionedString(bitmap);
        s.consume('0');
        s.consume('x');
        addAttribute(attribute, value, new BigInteger(s.substring().trim(),16));
    }

    /**
     * Adds attributes with the specified bitmap to normalizer.
     * @param attribute Attribute to add
     * @param value Value of attribute
     * @param bitmap Bitmap as a list of bits, e.g. '[0, 3, 45]'
     */
    private void parseBitmapList(String attribute, String value, String bitmap) {
        PositionedString s = new PositionedString(bitmap);
        s.consume('[');
        BigInteger mask = BigInteger.ZERO;
        while (!s.peek(']')) {
            s.consumeSpaces();
            int pos = findNextButSkipLists(new char[]{',',']'}, s.string(), s.position());
            if (pos == -1) {
                break;
            }
            int subqueryIndex = Integer.parseUnsignedInt(s.substring(pos).trim());
            if (subqueryIndex > 63 || subqueryIndex < 0) {
                throw new IllegalArgumentException("Subquery index must be in the range 0-63");
            }
            mask = mask.or(BigInteger.ONE.shiftLeft(subqueryIndex));
            s.setPosition(pos);
            s.consumeOptional(',');
            s.consumeSpaces();
        }
        addAttribute(attribute, value, mask);
    }

    /**
     * Add an attribute without a subquery mask
     * @param attribute name of attribute
     * @param value value of attribute
     */
    protected abstract void addAttribute(String attribute, String value);

    /**
     * Add an attribute with a subquery mask
     * @param attribute name of attribute
     * @param value value of attribute
     * @param subqueryMask subquery mask for attribute (64-bit)
     */
    protected abstract void addAttribute(String attribute, String value, BigInteger subqueryMask);

    /**
     * Finds next index of a set of chars, but skips past any lists ("[...]").
     * @param chars Characters to find. Note that '[' should not be in this list.
     * @param s String to search
     * @param position position in s to start at.
     * @return position of first char from "chars" that does not appear within brackets.
     */
    private static int findNextButSkipLists(char[] chars, String s, int position) {
        for (; position<s.length(); position++) {
            if (s.charAt(position)=='[') {
                position = findNextButSkipLists(new char[]{']'}, s, position + 1);
                if (position<0) return -1;
            } else {
                for (char c : chars) {
                    if (s.charAt(position)==c)
                        return position;
                }
            }
        }
        return -1;
    }
}

