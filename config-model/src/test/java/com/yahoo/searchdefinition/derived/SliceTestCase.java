// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.component.ComponentId;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileConfigurer;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class SliceTestCase extends AbstractExportingTestCase {

    @Test
    public void testSlice() throws IOException, ParseException {
        ComponentId.resetGlobalCountersForTests();
        DerivedConfiguration c = assertCorrectDeriving("slice");
    }

}
