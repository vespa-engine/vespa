// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.search.Query;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests blending rules
 *
 * @author bratseth
 */
public class BlendingTestCase extends RuleBaseAbstractTestCase {

    public BlendingTestCase() {
        super("blending.sr");
    }

    /** Tests parameter literal matching */
    @Test
    public void testLiteralEquals() {
        assertParameterSemantics("AND a sun came cd","a sun came cd","search","[music]");
        assertParameterSemantics("AND driving audi","driving audi","search","[cars]");
        //assertParameterSemantics("AND audi music quality","audi music quality","search","carstereos",1);
    }

    private void assertParameterSemantics(String producedQuery, String inputQuery,
                                          String producedParameterName, String producedParameterValue) {
        assertParameterSemantics(producedQuery,inputQuery,producedParameterName,producedParameterValue,0);
    }

    private void assertParameterSemantics(String producedQuery,String inputQuery,
                                          String producedParameterName,String producedParameterValue,
                                          int tracing) {
        Query query=assertSemantics(producedQuery,inputQuery,tracing);
        assertEquals(producedParameterValue, query.properties().getString(producedParameterName));
    }

}
