// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.vespa.model.test.utils.VespaModelCreatorWithFilePkg;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

/**
 * @author bjorncs
 */
public class GlobalDistributionValidatorTest {

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void throws_exception_if_redudancy_does_not_imply_global_distribution() {
        Fixture fixture = new Fixture()
                .addGlobalDocument(createDocumentType("foo"))
                .addGlobalDocument(createDocumentType("bar"));
        Redundancy redundancy = createRedundancyWithoutGlobalDistribution();

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "The following document types are marked as global, " +
                        "but do not have high enough redundancy to make the documents globally distributed: " +
                        "'bar', 'foo'. Redundancy is 2, expected 3.");
        validate(fixture, redundancy);
    }

    @Test
    public void validation_of_global_distribution_is_deactivated_if_multiple_bucket_spaces_is_enabled() {
        Fixture fixture = new Fixture()
                .addGlobalDocument(createDocumentType("foo"))
                .addGlobalDocument(createDocumentType("bar"));
        Redundancy redundancy = createRedundancyWithoutGlobalDistribution();

        validate(fixture, redundancy, true);
    }

    @Test
    public void throws_exception_if_searchable_copies_too_low() {
        Fixture fixture = new Fixture()
                .addGlobalDocument(createDocumentType("foo"))
                .addGlobalDocument(createDocumentType("bar"));
        Redundancy redundancy = createRedundancyWithTooFewSearchableCopies();

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "The following document types have the number of searchable copies less than redundancy: " +
                "'bar', 'foo'. Searchable copies is 1, while redundancy is 2.");
        validate(fixture, redundancy);
    }

    @Test
    public void validation_succeeds_when_globally_distributed_and_enough_searchable_copies() {
        Fixture fixture = new Fixture()
                .addGlobalDocument(createDocumentType("foo"));
        Redundancy redundancy = createRedundancyWithGlobalDistribution();
        validate(fixture, redundancy);
    }

    @Test
    public void validation_succeeds_on_no_documents() {
        new GlobalDistributionValidator()
                .validate(emptyMap(), emptySet(), createRedundancyWithoutGlobalDistribution(), false);
    }

    @Test
    public void validation_succeeds_on_no_global_documents() {
        Fixture fixture = new Fixture()
                .addNonGlobalDocument(createDocumentType("foo"));
        Redundancy redundancy = createRedundancyWithoutGlobalDistribution();
        validate(fixture, redundancy);
    }

    @Test
    public void throws_exception_if_referenced_document_not_global() {
        NewDocumentType parent = createDocumentType("parent");
        Fixture fixture = new Fixture()
                .addNonGlobalDocument(parent)
                .addNonGlobalDocument(createDocumentType("child", parent));
        Redundancy redundancy = createRedundancyWithoutGlobalDistribution();
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "The following document types are referenced from other documents, but are not globally distributed: 'parent'");
        validate(fixture, redundancy);
    }

    @Test
    public void validation_succeeds_if_referenced_document_is_global() {
        NewDocumentType parent = createDocumentType("parent");
        Fixture fixture = new Fixture()
                .addGlobalDocument(parent)
                .addNonGlobalDocument(createDocumentType("child", parent));
        Redundancy redundancy = createRedundancyWithGlobalDistribution();
        validate(fixture, redundancy);
    }

    @Test
    public void throws_exception_on_unknown_document() {
        NewDocumentType unknown = new NewDocumentType(new NewDocumentType.Name("unknown"));
        NewDocumentType child = createDocumentType("child", unknown);
        Fixture fixture = new Fixture()
                .addNonGlobalDocument(child);
        Redundancy redundancy = createRedundancyWithGlobalDistribution();
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "The following document types are referenced from other documents, but are not listed in services.xml: 'unknown'");
        validate(fixture, redundancy);
    }

    @Test
    public void throws_exception_if_referenced_document_not_global_end_to_end() {
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "The following document types are referenced from other documents, but are not globally distributed: 'parent'");
        new VespaModelCreatorWithFilePkg("src/test/cfg/application/validation/global_distribution_validation/").create();
    }

    private static Redundancy createRedundancyWithGlobalDistribution() {
        Redundancy redundancy = new Redundancy(2, 2, 2);
        redundancy.setTotalNodes(2);
        return redundancy;
    }

    private static Redundancy createRedundancyWithoutGlobalDistribution() {
        Redundancy redundancy = new Redundancy(2, 2, 2);
        redundancy.setTotalNodes(3);
        return redundancy;
    }

    private static Redundancy createRedundancyWithTooFewSearchableCopies() {
        Redundancy redundancy = new Redundancy(2, 2, 1);
        redundancy.setTotalNodes(2);
        return redundancy;
    }

    private static NewDocumentType createDocumentType(String name, NewDocumentType... references) {
        Set<NewDocumentType.Name> documentReferences = Stream.of(references).map(NewDocumentType::getFullName).collect(toSet());
        return new NewDocumentType(new NewDocumentType.Name(name), documentReferences);
    }

    private static void validate(Fixture fixture, Redundancy redundancy) {
        validate(fixture, redundancy, false);
    }

    private static void validate(Fixture fixture, Redundancy redundancy, boolean enableMultipleBucketSpaces) {
        new GlobalDistributionValidator()
                .validate(fixture.getDocumentTypes(), fixture.getGloballyDistributedDocuments(), redundancy, enableMultipleBucketSpaces);
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
