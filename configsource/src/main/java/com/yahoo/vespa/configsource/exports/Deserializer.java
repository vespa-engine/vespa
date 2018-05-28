// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.configsource.exports;

/**
 * @author hakon
 */
public interface Deserializer<T> {
    T deserialize(byte[] bytes);
}
