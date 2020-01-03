// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

public class FieldsetTestCase extends AbstractExportingTestCase {

    @Test
    public void testRankProfiles() throws IOException, ParseException {
        assertCorrectDeriving("fieldset");
    }

}
