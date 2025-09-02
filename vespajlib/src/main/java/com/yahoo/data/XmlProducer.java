// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data;

/**
 * Generic API for classes that contain data representable as XML.
 */
public interface XmlProducer {

    /**
     * Append the XML representation of this object's data to a StringBuilder.
     *
     * @param target the StringBuilder to append to.
     * @return the target passed in is also returned (to allow chaining).
     */
    StringBuilder writeXML(StringBuilder target);

    /**
     * Convenience method equivalent to:
     * writeXML(new StringBuilder()).toString()
     *
     * @return String containing XML representation of this object's data.
     */
    default String toXML() {
        return writeXML(new StringBuilder()).toString();
    }

}
