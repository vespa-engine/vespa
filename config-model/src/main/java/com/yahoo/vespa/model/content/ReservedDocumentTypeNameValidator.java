// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.documentmodel.NewDocumentType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ReservedDocumentTypeNameValidator {

    public static final List<String> ORDERED_RESERVED_NAMES = Collections.unmodifiableList(
            Arrays.asList("and", "false", "id", "not", "null", "or", "true"));
    public static final Set<String> RESERVED_NAMES = Collections.unmodifiableSet(new HashSet<>(ORDERED_RESERVED_NAMES));

    public void validate(Map<String, NewDocumentType> documentDefinitions) {
        List<String> conflictingNames = documentDefinitions.keySet().stream()
                .filter(this::isReservedName)
                .toList();
        if (!conflictingNames.isEmpty()) {
            throw new IllegalArgumentException(makeReservedNameMessage(conflictingNames));
        }
    }

    private boolean isReservedName(String name) {
        return RESERVED_NAMES.contains(name.toLowerCase());
    }

    private static String asQuotedListString(List<String> list) {
        return list.stream().map(s -> String.format("'%s'", s)).collect(Collectors.joining(", "));
    }

    private static String makeReservedNameMessage(List<String> conflictingNames) {
        return String.format("The following document types conflict with reserved keyword names: %s. Reserved keywords are %s",
                asQuotedListString(conflictingNames), asQuotedListString(ORDERED_RESERVED_NAMES));
    }

}
