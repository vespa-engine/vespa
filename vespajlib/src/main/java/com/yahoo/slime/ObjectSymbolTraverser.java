// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * Callback interface for traversing objects.
 * Implement this and call Inspector.traverse()
 * and you will get one callback for each field in an object.
 **/
public interface ObjectSymbolTraverser {

    /**
     * Callback function to implement.
     *
     * @param sym symbol id for the current field.
     * @param inspector accessor for the current field's value.
     */
    void field(int sym, Inspector inspector);

}
