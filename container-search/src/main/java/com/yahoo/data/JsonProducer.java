// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data;

/**
 * Generic API for classes that contain data representable as JSON.
 */
public interface JsonProducer {

    /**
     * Append the JSON representation of this object's data to a StringBuilder.
     *
     * @param target the StringBuilder to append to
     * @return the target passed in is also returned (to allow chaining)
     */
    StringBuilder writeJson(StringBuilder target);

    /**
     * Convenience method equivalent to
     * writeJson(new StringBuilder()).toString()
     *
     * @return a String containing JSON representation of this object's data
     */
    default String toJson() {
        return writeJson(new StringBuilder()).toString();
    }

}
