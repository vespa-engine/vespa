package com.yahoo.vespa.model.content;

import com.yahoo.documentmodel.NewDocumentType;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        Redundancy redundancy = createRedundancyWithGlobalDistributionValue(false);
        when(redundancy.effectiveFinalRedundancy()).thenReturn(1);
        when(redundancy.totalNodes()).thenReturn(2);

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "The following document types are marked as global, " +
                        "but do not have high enough redundancy to make the documents globally distributed: " +
                        "'bar', 'foo'. Redundancy is 1, expected 2.");
        new GlobalDistributionValidator()
                .validate(fixture.getDocumentTypes(), fixture.getGloballyDistributedDocuments(), redundancy);
    }

    @Test
    public void validation_succeeds_when_globally_distributed() {
        Fixture fixture = new Fixture()
                .addGlobalDocument(createDocumentType("foo"));
        Redundancy redundancy = createRedundancyWithGlobalDistributionValue(true);
        new GlobalDistributionValidator()
                .validate(fixture.getDocumentTypes(), fixture.getGloballyDistributedDocuments(), redundancy);
    }

    @Test
    public void validation_succeeds_on_no_documents() {
        new GlobalDistributionValidator()
                .validate(emptyMap(), emptySet(), createRedundancyWithGlobalDistributionValue(false));
    }

    @Test
    public void validation_succeeds_on_no_global_documents() {
        Fixture fixture = new Fixture()
                .addNonGlobalDocument(createDocumentType("foo"));
        Redundancy redundancy = createRedundancyWithGlobalDistributionValue(false);
        new GlobalDistributionValidator()
                .validate(fixture.getDocumentTypes(), fixture.getGloballyDistributedDocuments(), redundancy);
    }

    @Test
    public void throws_exception_if_referenced_document_not_global() {
        NewDocumentType parent = createDocumentType("parent");
        Fixture fixture = new Fixture()
                .addNonGlobalDocument(parent)
                .addNonGlobalDocument(createDocumentType("child", parent));
        Redundancy redundancy = createRedundancyWithGlobalDistributionValue(false);
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "The following document types are referenced from other documents, but are not globally distributed: 'parent'");
        new GlobalDistributionValidator()
                .validate(fixture.getDocumentTypes(), fixture.getGloballyDistributedDocuments(), redundancy);
    }

    @Test
    public void validation_succeeds_if_referenced_document_is_global() {
        NewDocumentType parent = createDocumentType("parent");
        Fixture fixture = new Fixture()
                .addGlobalDocument(parent)
                .addNonGlobalDocument(createDocumentType("child", parent));
        Redundancy redundancy = createRedundancyWithGlobalDistributionValue(true);
        new GlobalDistributionValidator()
                .validate(fixture.getDocumentTypes(), fixture.getGloballyDistributedDocuments(), redundancy);
    }

    @Test
    public void throws_exception_on_unknown_document() {
        NewDocumentType unknown = new NewDocumentType(new NewDocumentType.Name("unknown"));
        NewDocumentType child = createDocumentType("child", unknown);
        Fixture fixture = new Fixture()
                .addNonGlobalDocument(child);
        Redundancy redundancy = createRedundancyWithGlobalDistributionValue(true);
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "The following document types are referenced from other documents, but are not listed in services.xml: 'unknown'");
        new GlobalDistributionValidator()
                .validate(fixture.getDocumentTypes(), fixture.getGloballyDistributedDocuments(), redundancy);
    }

    private static Redundancy createRedundancyWithGlobalDistributionValue(boolean isGloballyDistributed) {
        Redundancy redundancy = mock(Redundancy.class);
        when(redundancy.isEffectivelyGloballyDistributed()).thenReturn(isGloballyDistributed);
        return redundancy;
    }

    private static NewDocumentType createDocumentType(String name, NewDocumentType... references) {
        Set<NewDocumentType.Name> documentReferences = Stream.of(references).map(NewDocumentType::getFullName).collect(toSet());
        return new NewDocumentType(new NewDocumentType.Name(name), documentReferences);
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
