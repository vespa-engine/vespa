// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

/**
 * @author hakonhall
 */
public interface FlagSerializer<T> extends Serializer<T>, Deserializer<T> {
}
