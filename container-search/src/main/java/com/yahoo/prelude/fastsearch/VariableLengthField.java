// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

/**
 * Interface to easier find the start of the actual data for variable length
 * fields.
 *
 * @author Steinar Knutsen
 */
public interface VariableLengthField {

    int sizeOfLength();

}
