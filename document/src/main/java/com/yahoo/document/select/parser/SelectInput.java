// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.parser;

import com.yahoo.javacc.FastCharStream;

/**
 * @author Simon Thoresen Hult
 */
public class SelectInput extends FastCharStream implements CharStream {

    public SelectInput(String input) {
        super(input);
    }
}
