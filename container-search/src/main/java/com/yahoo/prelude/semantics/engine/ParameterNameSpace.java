// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.engine;

import com.yahoo.search.Query;

/**
 * A name space representing the (http) parameters following this query
 *
 * @author bratseth
 */
public class ParameterNameSpace extends NameSpace {

    public boolean matches(String term,RuleEvaluation e) {
        Query query=e.getEvaluation().getQuery();
        String value=query.properties().getString(term);
        if (value==null) return false;
        e.setValue(value);
        return true;
    }

}
