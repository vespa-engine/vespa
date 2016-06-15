// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.parser;

import com.yahoo.javacc.FastCharStream;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
@SuppressWarnings("deprecation")
public class SimpleCharStream extends FastCharStream implements com.yahoo.searchdefinition.parser.CharStream,
                                                                com.yahoo.vespa.indexinglanguage.parser.CharStream
{
    public SimpleCharStream(String input) {
        super(input);
    }
}
