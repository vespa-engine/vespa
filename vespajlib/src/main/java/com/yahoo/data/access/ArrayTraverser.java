// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.data.access;

/**
 * Callback interface for traversing arrays.
 * Implement this and call Inspector.traverse()
 * and you will get one callback for each array entry.
 */
public interface ArrayTraverser {

    /**
     * Callback function to implement.
     * @param idx array index for the current array entry.
     * @param inspector accessor for the current array entry's value.
     */
    void entry(int idx, Inspector inspector);

}
