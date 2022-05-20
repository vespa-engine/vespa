// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.parser;

import com.yahoo.javacc.FastCharStream;

/**
 * @author Simon Thoresen Hult
 */
public class SimpleCharStream extends FastCharStream implements com.yahoo.schema.parser.CharStream,
                                                                com.yahoo.vespa.indexinglanguage.parser.CharStream {

    public SimpleCharStream(String input) {
        super(input);
    }

}
