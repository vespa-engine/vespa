// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.disclosure;

/**
 * An interface for writing structured data to a sink, for instance a data structure or output format.
 *
 * @author havardpe
 * @author bjorncs
 */
public interface DataSink {

    void fieldName(String name);

    void startObject();

    void endObject();

    void startArray();

    void endArray();

    void emptyValue();

    void booleanValue(boolean v);

    void longValue(long v);

    void doubleValue(double v);

    void stringValue(String utf16);

    void stringValue(byte[] utf8);

    void stringValue(String utf16, byte[] utf8);

}
