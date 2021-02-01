// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.test;

import com.yahoo.prelude.hitfield.XMLString;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.text.BooleanParser;

/**
 * A searcher adding a new hit.
 *
 * @author  Joe Developer
 */
public class SimpleSearcher extends Searcher {

    public Result search(Query query,Execution execution) {
            BooleanParser.parseBoolean("true");
            XMLString xmlString = new XMLString("<sampleXmlString/>");

            Hit hit = new Hit("Hello world!");

            Result result = execution.search(query);
            result.hits().add(hit);
            return result;
    }
}
