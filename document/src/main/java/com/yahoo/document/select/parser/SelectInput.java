// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.select.parser;

import com.yahoo.javacc.FastCharStream;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen Hult</a>
 */
public class SelectInput extends FastCharStream implements CharStream {

    public SelectInput(String input) {
        super(input);
    }
}
