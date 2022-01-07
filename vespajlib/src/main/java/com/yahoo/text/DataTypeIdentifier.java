// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

/**
 * @author baldersheim
 */
public class DataTypeIdentifier {

    private static final byte [] ARRAY = {'a', 'r', 'r', 'a', 'y'};
    private static final byte [] ANNOTATIONREFERENCE = {'a','n','n','o','t','a','t','i','o','n','r','e','f','e','r','e','n','c','e'};
    private static final byte [] MAP = { 'm', 'a', 'p'};
    private static final byte [] WSET = {'w', 'e', 'i', 'g', 'h', 't', 'e', 'd', 's', 'e', 't'};
    private static final byte [] CREATEIFNONEXISTENT = {';','a', 'd', 'd'};
    private static final byte [] REMOVEIFZERO = {';','r', 'e', 'm', 'o', 'v', 'e'};
    private static final byte [] CREATANDREMOVE = {';','a', 'd', 'd',';','r', 'e', 'm', 'o', 'v', 'e'};
    private static final byte [] EMPTY = {};
    private Utf8String utf8;
    public DataTypeIdentifier(String s) {
        utf8 = new Utf8String(s);
        verify(utf8.wrap().array());
    }
    public DataTypeIdentifier(AbstractUtf8Array utf8) {
        this.utf8 = new Utf8String(utf8);
        verify(utf8.wrap().array());
    }
    public DataTypeIdentifier(byte [] utf8) {
        this(new Utf8Array(utf8));
    }

    private DataTypeIdentifier(final byte [] prefix, DataTypeIdentifier nested, final byte [] postfix) {
        utf8 = new Utf8String(new Utf8Array(createPrefixDataType(prefix, nested, postfix)));
    }
    private DataTypeIdentifier(final byte [] prefix, DataTypeIdentifier key, DataTypeIdentifier value) {
        utf8 = new Utf8String(new Utf8Array(createMapDataType(prefix, key, value)));
    }

    public static DataTypeIdentifier createArrayDataTypeIdentifier(DataTypeIdentifier nested) {
        return new DataTypeIdentifier(ARRAY, nested, EMPTY);
    }
    public static DataTypeIdentifier createAnnotationReferenceDataTypeIdentifier(DataTypeIdentifier nested) {
        return new DataTypeIdentifier(ANNOTATIONREFERENCE, nested, EMPTY);
    }
    public static DataTypeIdentifier createMapDataTypeIdentifier(DataTypeIdentifier key, DataTypeIdentifier value) {
        return new DataTypeIdentifier(MAP, key, value);
    }
    public static DataTypeIdentifier createWeightedSetTypeIdentifier(DataTypeIdentifier nested, boolean createIfNonExistent, boolean removeIfZero) {
        return new DataTypeIdentifier(WSET, nested, createPostfix(createIfNonExistent, removeIfZero));
    }
    @Override
    public int hashCode() {
        return utf8.hashCode();
    }
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof DataTypeIdentifier) {
            return utf8.equals(((DataTypeIdentifier)obj).utf8);
        }
        return false;
    }
    @Override
    public String toString() {
        return utf8.toString();
    }
    public final Utf8String getUtf8() {
        return utf8;
    }
    private static byte [] createPostfix(boolean createIfNonExistent, boolean removeIfZero) {
        if (createIfNonExistent && removeIfZero) {
            return CREATANDREMOVE;
        } else if (createIfNonExistent) {
            return CREATEIFNONEXISTENT;
        } else if (removeIfZero) {
            return REMOVEIFZERO;
        }
        return EMPTY;
    }
    private static byte [] createPrefixDataType(final byte [] prefix, final DataTypeIdentifier nested, final byte [] postfix) {
        byte [] whole = new byte[prefix.length + 2 + nested.utf8.getByteLength() + postfix.length];
        for (int i=0; i < prefix.length; i++) {
            whole[i] = prefix[i];
        }
        whole[prefix.length] = '<';
        for (int i = 0, m=nested.utf8.getByteLength(); i < m; i++ ) {
            whole[prefix.length+1+i] = nested.utf8.getByte(i);
        }
        whole[prefix.length + 1 + nested.utf8.getByteLength()] = '>';
        for (int i = 0; i < postfix.length; i++) {
            whole[prefix.length + 1 + nested.utf8.length() + 1 + i] = postfix[i];
        }
        return whole;
    }
    private static byte [] createMapDataType(final byte [] prefix, final DataTypeIdentifier key, final DataTypeIdentifier value) {
        byte [] whole = new byte[prefix.length + 3 + key.utf8.getByteLength() + value.utf8.getByteLength()];
        for (int i=0; i < prefix.length; i++) {
            whole[i] = prefix[i];
        }
        whole[prefix.length] = '<';
        for (int i = 0, m=key.utf8.getByteLength(); i < m; i++ ) {
            whole[prefix.length+1+i] = key.utf8.getByte(i);
        }
        whole[prefix.length + 1 + key.utf8.getByteLength()] = ',';
        for (int i = 0; i < value.utf8.getByteLength(); i++) {
            whole[prefix.length + 1 + key.utf8.getByteLength() + 1 + i] = value.utf8.getByte(i);
        }
        whole[whole.length-1] = '>';
        return whole;
    }
    private static byte [] verify(final byte [] utf8) {
        if (utf8.length > 0) {
            verifyFirst(utf8[0], utf8);
            for (int i=1; i < utf8.length; i++) {
                verifyAny(utf8[i], utf8);
            }
        }
        return utf8;

    }
    private static boolean verifyFirst(byte c, byte [] identifier) {
        if (!((c == '_')  || ((c >= 'a') && (c <= 'z')))) {
            throw new IllegalArgumentException("Illegal starting character '" + (char)c + "' of identifier '" + new Utf8String(new Utf8Array(identifier)).toString() +"'.");
        }
        return true;
    }
    private static boolean verifyAny(byte c, byte [] identifier) {
        if (!((c == '_') || (c == '.') || ((c >= 'a') && (c <= 'z')) || ((c >= '0') && (c <= '9')))) {
            throw new IllegalArgumentException("Illegal character '" + (char)c + "' of identifier '" + new Utf8String(new Utf8Array(identifier)).toString() +"'.");
        }
        return true;
    }

}
