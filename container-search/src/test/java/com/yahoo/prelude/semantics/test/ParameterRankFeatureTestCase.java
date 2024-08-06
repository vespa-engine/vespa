// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.search.Query;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests generating a rank feature (input) parameter from query content.
 *
 * @author bratseth
 */
public class ParameterRankFeatureTestCase extends RuleBaseAbstractTestCase {

    public ParameterRankFeatureTestCase() {
        super("parameter-rankfeature.sr");
    }

    /** Tests parameter production */
    @Test
    void testParameterProduction() {
        Query query = new Query("?query=youtube%20cat%20videos");
        assertSemantics("WEAKAND(100) youtube cat videos", query);
        assertEquals(1.0, query.getRanking().getFeatures().getDouble("isYoutube").getAsDouble());
    }

}
