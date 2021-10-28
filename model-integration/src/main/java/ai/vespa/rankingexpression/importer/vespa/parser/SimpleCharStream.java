// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.rankingexpression.importer.vespa.parser;

import com.yahoo.javacc.FastCharStream;

public class SimpleCharStream extends FastCharStream implements ai.vespa.rankingexpression.importer.vespa.parser.CharStream {

    public SimpleCharStream(String input) {
        super(input);
    }

}
