// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.indexinglanguage.parser;

import com.yahoo.javacc.FastCharStream;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public final class IndexingInput extends FastCharStream implements CharStream {

    public IndexingInput(String input) {
        super(input);
    }
}
