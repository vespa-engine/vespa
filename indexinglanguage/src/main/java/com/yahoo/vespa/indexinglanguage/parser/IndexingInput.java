// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import com.yahoo.javacc.FastCharStream;

/**
 * @author Simon Thoresen Hult
 */
public final class IndexingInput extends FastCharStream implements CharStream {

    public IndexingInput(String input) {
        super(input);
    }

}
