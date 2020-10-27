// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.documentmodel.NewDocumentType;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class ReservedDocumentTypeNameValidatorTest {

    private static Map<String, NewDocumentType> asDocTypeMapping(List<String> typeNames) {
        return typeNames.stream().collect(Collectors.toMap(Function.identity(), n -> new NewDocumentType(new NewDocumentType.Name(n))));
    }

    @Test
    public void exception_thrown_on_reserved_names() {
        // Ensure ordering is consistent for testing
        Map<String, NewDocumentType> orderedDocTypes = new TreeMap<>(asDocTypeMapping(ReservedDocumentTypeNameValidator.ORDERED_RESERVED_NAMES));

        ReservedDocumentTypeNameValidator validator = new ReservedDocumentTypeNameValidator();

        Exception e = assertThrows(IllegalArgumentException.class,
                () -> validator.validate(orderedDocTypes));
        assertEquals("The following document types conflict with reserved keyword names: " +
                     "'and', 'false', 'id', 'not', 'null', 'or', 'true'. " +
                     "Reserved keywords are 'and', 'false', 'id', 'not', 'null', 'or', 'true'",
                e.getMessage());
    }

    @Test
    public void exception_is_not_thrown_on_unreserved_name() {
        ReservedDocumentTypeNameValidator validator = new ReservedDocumentTypeNameValidator();
        validator.validate(asDocTypeMapping(Collections.singletonList("foo")));
    }

    @Test
    public void validation_is_case_insensitive() {
        ReservedDocumentTypeNameValidator validator = new ReservedDocumentTypeNameValidator();
        Map<String, NewDocumentType> orderedDocTypes = new TreeMap<>(asDocTypeMapping(Arrays.asList("NULL", "True", "anD")));

        Exception e = assertThrows(IllegalArgumentException.class, () -> validator.validate(orderedDocTypes));
        assertThat(e.getMessage(), containsString("The following document types conflict with reserved keyword names: " +
                                                  "'NULL', 'True', 'anD'."));
    }

}
