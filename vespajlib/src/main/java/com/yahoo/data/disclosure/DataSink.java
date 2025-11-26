// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.disclosure;

import com.yahoo.api.annotations.Beta;

/**
 * An interface for disclosing structured data, for instance, to another data structure or output format.
 *
 * @author havardpe
 * @author bjorncs
 * @author andreer
 */
@Beta
public interface DataSink {

    void fieldName(String name);

    void fieldName(byte[] utf8);

    void fieldName(String utf16, byte[] utf8);

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

    default void floatValue(float v) {
        doubleValue(v);
    }

    void doubleValue(double v);

    void stringValue(String utf16);

    void stringValue(byte[] utf8);

    void stringValue(String utf16, byte[] utf8);

    void dataValue(byte[] data);

}
