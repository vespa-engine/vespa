// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.javacc.FastCharStream;

/**
 * @author Simon Thoresen Hult
 */
@SuppressWarnings("deprecation")
public class SimpleCharStream extends FastCharStream implements com.yahoo.searchdefinition.parser.CharStream,
                                                                com.yahoo.vespa.indexinglanguage.parser.CharStream
{
    public SimpleCharStream(String input) {
        super(input);
    }
}
