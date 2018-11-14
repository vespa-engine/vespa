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
