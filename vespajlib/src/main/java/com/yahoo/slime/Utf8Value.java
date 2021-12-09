// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * A value type encapsulating a String in its UTF-8 representation.
 * Useful for lazy decoding; if the data is just passed through in
 * UTF-8 it will never be converted at all.
 *
 * @author havardpe
 */
final class Utf8Value extends Value {

    private final byte[] value;
    private String string;
    private Utf8Value(byte[] value) { this.value = value; }
    public static Value create(byte[] value) {
        if (value == null) {
            return NixValue.instance();
        } else {
            return new Utf8Value(value);
        }
    }
    public Type type() { return Type.STRING; }
    public String asString() {
        if (string == null) {
            string = Utf8Codec.decode(value, 0, value.length);
        }
        return string;
    }
    public byte[] asUtf8() { return value; }
    public void accept(Visitor v) { v.visitString(value); }

}
