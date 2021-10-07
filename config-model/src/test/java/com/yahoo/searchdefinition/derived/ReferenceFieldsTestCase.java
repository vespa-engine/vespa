// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;

/**
 * @author geirst
 */
public class ReferenceFieldsTestCase extends AbstractExportingTestCase {

    @Test
    public void configs_related_to_reference_fields_are_derived() throws IOException, ParseException {
        assertCorrectDeriving("reference_fields", "ad", new TestableDeployLogger());
    }
}
