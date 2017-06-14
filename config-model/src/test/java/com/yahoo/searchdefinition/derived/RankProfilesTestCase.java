// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * Tests a search definition with various rank profiles having different settings
 *
 * @author  bratseth
 */
public class RankProfilesTestCase extends AbstractExportingTestCase {
    @Test
    public void testRankProfiles() throws IOException, ParseException {
        assertCorrectDeriving("rankprofiles");
    }
}
