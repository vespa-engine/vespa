// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.hitfield;

/**
 * Represents an element of a hit property
 *
 * @author Steinar Knutsen
 */
public interface FieldPart {

    boolean isFinal();
    boolean isToken();
    String getContent();
    String toString();

}
