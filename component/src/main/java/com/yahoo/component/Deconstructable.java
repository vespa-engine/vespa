// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component;

/**
 * A component in the component graph that should be deconstructed, to release resources.
 *
 * @author jonmv
 */
public interface Deconstructable {

    void deconstruct();

}
