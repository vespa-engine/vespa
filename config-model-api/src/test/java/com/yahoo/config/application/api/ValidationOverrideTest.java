// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.application.api;

import com.yahoo.test.ManualClock;
import org.junit.Assert;
import org.junit.Test;
import java.io.StringReader;
import java.time.Instant;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class ValidationOverrideTest {

    @Test
    public void testValidationOverridesInIsolation() {
        String validationOverrides =
                "<validation-overrides>" +
                "  <allow until='2000-01-01'>indexing-change</allow>" +
                "  <allow until='2000-01-03' comment='any text'>indexing-mode-change</allow>" +
                "</validation-overrides>";
        {

            ValidationOverrides overrides = ValidationOverrides.fromXml(new StringReader(validationOverrides));
            Instant now = ManualClock.at("2000-01-01T23:59:00");
            assertOverridden("indexing-change", overrides, now);
            assertOverridden("indexing-mode-change", overrides, now);
            assertNotOverridden("field-type-change", overrides, now);

            assertEquals(validationOverrides, overrides.xmlForm());
        }

        {
            ValidationOverrides overrides = ValidationOverrides.fromXml(new StringReader(validationOverrides));
            Instant now = ManualClock.at("2000-01-02T00:00:00");
            assertNotOverridden("indexing-change", overrides, now);
            assertOverridden("indexing-mode-change", overrides, now);
            assertNotOverridden("field-type-change", overrides, now);
        }

        {
            ValidationOverrides overrides = ValidationOverrides.fromXml(new StringReader(validationOverrides));
            Instant now = ManualClock.at("2000-01-04T00:00:00");
            assertNotOverridden("indexing-change", overrides, now);
            assertNotOverridden("indexing-mode-change", overrides, now);
            assertNotOverridden("field-type-change", overrides, now);
        }

    }

    @Test
    public void testInvalidOverridePeriod() {
        String validationOverrides =
                "<validation-overrides>" +
                "  <allow until='2000-02-02'>indexing-change</allow>" +
                "</validation-overrides>";

        try {
            ValidationOverrides overrides = ValidationOverrides.fromXml(new StringReader(validationOverrides));
            Instant now = ManualClock.at("2000-01-01T23:59:00");
            overrides.allows("indexing-change", now);
            overrides.validate(now);
            Assert.fail("Expected validation interval override validation validation failure");
        }
        catch (IllegalArgumentException e) {
            Assert.assertEquals("validation-overrides is invalid: allow 'indexing-change' until 2000-02-03T00:00:00Z is too far in the future: Max 30 days is allowed",
                                e.getMessage());
        }
    }
    
    @Test
    public void testEmpty() {
        ValidationOverrides empty = ValidationOverrides.empty;
        ValidationOverrides emptyReserialized = ValidationOverrides.fromXml(empty.xmlForm());
        assertEquals(empty.xmlForm(), emptyReserialized.xmlForm());
    }

    private void assertOverridden(String validationId, ValidationOverrides overrides, Instant now) {
        overrides.invalid(ValidationId.from(validationId).get(), "message", now); // should not throw exception
    }

    private void assertNotOverridden(String validationId, ValidationOverrides overrides, Instant now) {
        try {
            overrides.invalid(ValidationId.from(validationId).get(), "message", now);
            Assert.fail("Expected '" + validationId + "' to not be overridden");
        }
        catch (ValidationOverrides.ValidationException expected) {
        }
    }

    @Test
    public void testSchemaRemovalAliasForContentTypeRemoval() {
        String validationOverridesContentType =
                "<validation-overrides>" +
                "  <allow until='2000-02-02'>content-type-removal</allow>" +
                "</validation-overrides>";
        ValidationOverrides overrideContentTypeRemoval = ValidationOverrides
                .fromXml(new StringReader(validationOverridesContentType));

        String validationOverridesSchema =
                "<validation-overrides>" +
                        "  <allow until='2000-02-02'>schema-removal</allow>" +
                        "</validation-overrides>";
        ValidationOverrides overrideSchemaRemoval = ValidationOverrides
                .fromXml(new StringReader(validationOverridesSchema));


        Instant now = ManualClock.at("2000-01-01T23:59:00");
        assertOverridden("content-type-removal", overrideContentTypeRemoval, now);
        assertOverridden("schema-removal", overrideSchemaRemoval, now);
    }
}
