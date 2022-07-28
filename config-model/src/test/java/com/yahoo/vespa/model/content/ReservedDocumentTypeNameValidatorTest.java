// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.documentmodel.NewDocumentType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ReservedDocumentTypeNameValidatorTest {

    private static Map<String, NewDocumentType> asDocTypeMapping(List<String> typeNames) {
        return typeNames.stream().collect(Collectors.toMap(Function.identity(), n -> new NewDocumentType(new NewDocumentType.Name(n))));
    }

    @Test
    void exception_thrown_on_reserved_names() {
        // Ensure ordering is consistent for testing
        Map<String, NewDocumentType> orderedDocTypes = new TreeMap<>(asDocTypeMapping(ReservedDocumentTypeNameValidator.ORDERED_RESERVED_NAMES));

        ReservedDocumentTypeNameValidator validator = new ReservedDocumentTypeNameValidator();
        try {
            validator.validate(orderedDocTypes);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("The following document types conflict with reserved keyword names: " +
                    "'and', 'false', 'id', 'not', 'null', 'or', 'true'. " +
                    "Reserved keywords are 'and', 'false', 'id', 'not', 'null', 'or', 'true'",
                    e.getMessage());
        }
    }

    @Test
    void exception_is_not_thrown_on_unreserved_name() {
        ReservedDocumentTypeNameValidator validator = new ReservedDocumentTypeNameValidator();
        validator.validate(asDocTypeMapping(Collections.singletonList("foo")));
    }

    @Test
    void validation_is_case_insensitive() {
        ReservedDocumentTypeNameValidator validator = new ReservedDocumentTypeNameValidator();
        Map<String, NewDocumentType> orderedDocTypes = new TreeMap<>(asDocTypeMapping(Arrays.asList("NULL", "True", "anD")));
        try {
            validator.validate(orderedDocTypes);
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().startsWith("The following document types conflict with reserved keyword names: " +
                    "'NULL', 'True', 'anD'."));
        }
    }

}
