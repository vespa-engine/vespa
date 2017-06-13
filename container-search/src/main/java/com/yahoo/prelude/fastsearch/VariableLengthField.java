// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

/**
 * Interface to easier find the start of the actual data for variable length
 * fields.
 *
 * @author Steinar Knutsen
 */
public interface VariableLengthField {

    /** Returns the size of the length preceeding the data of this field, in bytes */
    int sizeOfLength();

}
