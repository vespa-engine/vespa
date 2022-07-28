// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.documentmodel.NewDocumentType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
        assertOrder(Arrays.asList("a"), new DocumentTypesBuilder().add("a"));
        assertOrder(Arrays.asList("a", "c", "b"),
                new DocumentTypesBuilder().add("a").add("c").add("b"));
    }

    @Test
    void require_that_types_with_references_are_sorted_in_topological_order() {
        assertOrder(Arrays.asList("b", "a"), new DocumentTypesBuilder()
                .add("a", Arrays.asList("b"))
                .add("b"));
        assertOrder(Arrays.asList("c", "b", "a"), new DocumentTypesBuilder()
                .add("a", Arrays.asList("b", "c"))
                .add("b", Arrays.asList("c"))
                .add("c"));
        assertOrder(Arrays.asList("b", "a", "d", "c"), new DocumentTypesBuilder()
                .add("a", Arrays.asList("b"))
                .add("b")
                .add("c", Arrays.asList("d"))
                .add("d"));
    }

    private void assertOrder(List<String> expOrder, DocumentTypesBuilder builder) {
        List<NewDocumentType> sortedDocTypes = TopologicalDocumentTypeSorter.sort(builder.build());
        List<String> actOrder = sortedDocTypes.stream().map(NewDocumentType::getName).collect(Collectors.toList());
        assertEquals(expOrder, actOrder);
    }

    private static class DocumentTypesBuilder {

        private final List<NewDocumentType> result = new ArrayList<>();

        public DocumentTypesBuilder add(String docTypeName) {
            return add(docTypeName, Collections.emptyList());
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
