// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author bjorncs
 */
public class GlobalDistributionValidatorTest {

    @Test
    void validation_succeeds_on_no_documents() {
        new GlobalDistributionValidator()
                .validate(emptyMap(), emptySet());
    }

    @Test
    void validation_succeeds_on_no_global_documents() {
        Fixture fixture = new Fixture()
                .addNonGlobalDocument(createDocumentType("foo"));
        validate(fixture);
    }

    @Test
    void throws_exception_if_referenced_document_not_global() {
        NewDocumentType parent = createDocumentType("parent");
        Fixture fixture = new Fixture()
                .addNonGlobalDocument(parent)
                .addNonGlobalDocument(createDocumentType("child", parent));
        try {
            validate(fixture);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("The following document types are referenced from other documents, but are not globally distributed: 'parent'",
                    e.getMessage());
        }
    }

    @Test
    void validation_succeeds_if_referenced_document_is_global() {
        NewDocumentType parent = createDocumentType("parent");
        Fixture fixture = new Fixture()
                .addGlobalDocument(parent)
                .addNonGlobalDocument(createDocumentType("child", parent));
        validate(fixture);
    }

    @Test
    void throws_exception_on_unknown_document() {
        NewDocumentType unknown = new NewDocumentType(new NewDocumentType.Name("unknown"));
        NewDocumentType child = createDocumentType("child", unknown);
        Fixture fixture = new Fixture()
                .addNonGlobalDocument(child);

        try {
            validate(fixture);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("The following document types are referenced from other documents, but are not listed in services.xml: 'unknown'",
                    e.getMessage());
        }
    }

    @Test
    void throws_exception_if_referenced_document_not_global_end_to_end() {
        try {
            new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/global_distribution_validation/").create();
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("The following document types are referenced from other documents, but are not globally distributed: 'parent'",
                    e.getMessage());
        }
    }

    private static NewDocumentType createDocumentType(String name, NewDocumentType... references) {
        Set<NewDocumentType.Name> documentReferences = Stream.of(references).map(NewDocumentType::getFullName).collect(toSet());
        return new NewDocumentType(new NewDocumentType.Name(name), documentReferences);
    }

    private static void validate(Fixture fixture) {
        new GlobalDistributionValidator()
                .validate(fixture.getDocumentTypes(), fixture.getGloballyDistributedDocuments());
    }

    private static class Fixture {
        private final Map<String, NewDocumentType> documentTypes = new HashMap<>();
        private final Set<NewDocumentType> globallyDistributedDocuments = new HashSet<>();

        public Fixture addGlobalDocument(NewDocumentType documentType) {
            addDocument(documentType, true);
            return this;
        }

        public Fixture addNonGlobalDocument(NewDocumentType documentType) {
            addDocument(documentType, false);
            return this;
        }

        private void addDocument(NewDocumentType documentType, boolean isGlobal) {
            if (isGlobal) {
                globallyDistributedDocuments.add(documentType);
            }
            documentTypes.put(documentType.getName(), documentType);
        }

        public Map<String, NewDocumentType> getDocumentTypes() {
            return documentTypes;
        }

        public Set<NewDocumentType> getGloballyDistributedDocuments() {
            return globallyDistributedDocuments;
        }
    }
}
