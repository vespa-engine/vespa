// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config;

/**
* @author Ulf Lilleengen
*/
public interface Serializer {
    Serializer createInner(String name);
    Serializer createArray(String name);
    Serializer createInner();
    Serializer createMap(String name);

    /**
     * Serialize leaf values.
     */
    void serialize(String name, boolean value);
    void serialize(String name, double value);
    void serialize(String name, long value);
    void serialize(String name, int value);
    void serialize(String name, String value);

    /**
     * Serialize array values.
     */
    void serialize(boolean value);
    void serialize(double value);
    void serialize(long value);
    void serialize(int value);
    void serialize(String value);

}
