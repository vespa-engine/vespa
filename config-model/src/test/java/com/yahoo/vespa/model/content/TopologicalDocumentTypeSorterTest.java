// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.documentmodel.NewDocumentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author geirst
 */
public class TopologicalDocumentTypeSorterTest {

    @Test
    void require_that_types_without_references_are_returned_in_input_order() {
        assertOrder(List.of("a"), new DocumentTypesBuilder().add("a"));
        assertOrder(List.of("a", "c", "b"),
                new DocumentTypesBuilder().add("a").add("c").add("b"));
    }

    @Test
    void require_that_types_with_references_are_sorted_in_topological_order() {
        assertOrder(List.of("b", "a"), new DocumentTypesBuilder()
                .add("a", List.of("b"))
                .add("b"));
        assertOrder(List.of("c", "b", "a"), new DocumentTypesBuilder()
                .add("a", List.of("b", "c"))
                .add("b", List.of("c"))
                .add("c"));
        assertOrder(List.of("b", "a", "d", "c"), new DocumentTypesBuilder()
                .add("a", List.of("b"))
                .add("b")
                .add("c", List.of("d"))
                .add("d"));
    }

    private void assertOrder(List<String> expOrder, DocumentTypesBuilder builder) {
        List<NewDocumentType> sortedDocTypes = TopologicalDocumentTypeSorter.sort(builder.build());
        List<String> actOrder = sortedDocTypes.stream().map(NewDocumentType::getName).toList();
        assertEquals(expOrder, actOrder);
    }

    private static class DocumentTypesBuilder {

        private final List<NewDocumentType> result = new ArrayList<>();

        public DocumentTypesBuilder add(String docTypeName) {
            return add(docTypeName, List.of());
        }

        public DocumentTypesBuilder add(String docTypeName, List<String> docTypeNameReferences) {
            Set<NewDocumentType.Name> documentReferences =
                    docTypeNameReferences.stream().map(NewDocumentType.Name::new).collect(Collectors.toSet());
            result.add(new NewDocumentType(new NewDocumentType.Name(docTypeName), documentReferences));
            return this;
        }

        public List<NewDocumentType> build() {
            return result;
        }
    }
}
