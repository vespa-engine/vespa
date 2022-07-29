// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * @author geirst
 */
public class ImportedFieldsTestCase extends AbstractExportingTestCase {

    @Test
    void configs_for_imported_fields_are_derived() throws IOException, ParseException {
        assertCorrectDeriving("importedfields", "child", new TestableDeployLogger());
    }

    @Test
    void configs_for_imported_struct_fields_are_derived() throws IOException, ParseException {
        assertCorrectDeriving("imported_struct_fields", "child",
                new TestProperties(),
                new TestableDeployLogger());
    }

    @Test
    void configs_for_imported_position_field_are_derived() throws IOException, ParseException {
        assertCorrectDeriving("imported_position_field", "child", new TestableDeployLogger());
    }

    @Test
    void configs_for_imported_position_field_summary_are_derived() throws IOException, ParseException {
        assertCorrectDeriving("imported_position_field_summary", "child", new TestableDeployLogger());
    }

    @Test
    void derives_configs_for_imported_fields_when_reference_fields_are_inherited() throws IOException, ParseException {
        assertCorrectDeriving("imported_fields_inherited_reference", "child_c", new TestableDeployLogger());
    }

}
