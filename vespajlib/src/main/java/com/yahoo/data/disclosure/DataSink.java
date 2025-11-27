// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.disclosure;

import com.yahoo.api.annotations.Beta;

/**
 * An interface for disclosing structured data
 *
 * @author havardpe
 * @author bjorncs
 * @author andreer
 */
@Beta
public interface DataSink {

    void fieldName(String utf16, byte[] utf8);

    default void fieldName(String utf16) {
        fieldName(utf16, null);
    }

    default void fieldName(byte[] utf8) {
        fieldName(null, utf8);
    }

    void startObject();

    void endObject();

    void startArray();

    void endArray();

    void emptyValue();

    void booleanValue(boolean v);

    void longValue(long v);

    default void intValue(int v) {
        longValue(v);
    }

    default void shortValue(short v) {
        longValue(v);
    }

    default void byteValue(byte v) {
        longValue(v);
    }

    void doubleValue(double v);

    default void floatValue(float v) {
        doubleValue(v);
    }

    void stringValue(String utf16, byte[] utf8);

    default void stringValue(String utf16) {
        stringValue(utf16, null);
    }

    default void stringValue(byte[] utf8) {
        stringValue(null, utf8);
    }

    void dataValue(byte[] data);

}
