// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * @author geirst
 */
public class ReferenceFieldsTestCase extends AbstractExportingTestCase {

    @Test
    void configs_related_to_reference_fields_are_derived() throws IOException, ParseException {
        assertCorrectDeriving("reference_fields", "ad", new TestableDeployLogger());
    }
}
