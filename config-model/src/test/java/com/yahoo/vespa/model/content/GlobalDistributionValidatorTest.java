package com.yahoo.vespa.model.content;

import com.yahoo.documentmodel.NewDocumentType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
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
        Map<String, NewDocumentType> documentTypes = Stream.of("foo", "bar")
                .collect(toMap(identity(), name -> new NewDocumentType(new NewDocumentType.Name(name))));
        HashSet<NewDocumentType> globallyDistributedDocuments = new HashSet<>(documentTypes.values());
        Redundancy redundancy = createRedundancyWithGlobalDistributionValue(false);
        when(redundancy.effectiveFinalRedundancy()).thenReturn(1);
        when(redundancy.totalNodes()).thenReturn(2);

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "The following document types are marked as global, " +
                        "but do not have high enough redundancy to make the documents globally distributed: " +
                        "'bar', 'foo'. Redundancy is 1, expected 2.");
        new GlobalDistributionValidator()
                .validate(documentTypes, globallyDistributedDocuments, redundancy);
    }

    @Test
    public void validation_succeeds_when_globally_distributed() {
        Redundancy redundancy = createRedundancyWithGlobalDistributionValue(true);

        NewDocumentType document = new NewDocumentType(new NewDocumentType.Name("foo"));
        Map<String, NewDocumentType> documentTypes = singletonMap("foo", document);
        Set<NewDocumentType> globallyDistributedDocuments = singleton(document);

        new GlobalDistributionValidator()
                .validate(documentTypes, globallyDistributedDocuments, redundancy);
    }

    @Test
    public void validation_succeeds_on_no_documents() {
        new GlobalDistributionValidator()
                .validate(emptyMap(), emptySet(), createRedundancyWithGlobalDistributionValue(false));
    }

    @Test
    public void validation_succeeds_on_no_global_documents() {
        NewDocumentType document = new NewDocumentType(new NewDocumentType.Name("foo"));
        Map<String, NewDocumentType> documentTypes = singletonMap("foo", document);
        Redundancy redundancy = createRedundancyWithGlobalDistributionValue(false);

        new GlobalDistributionValidator()
                .validate(documentTypes, emptySet(), redundancy);
    }

    @Test
    public void throws_exception_if_referenced_document_not_global() {
        NewDocumentType parentDocument = new NewDocumentType(new NewDocumentType.Name("parent"));
        NewDocumentType childDocument = new NewDocumentType(
                new NewDocumentType.Name("child"), singleton(parentDocument.getFullName()));

        Map<String, NewDocumentType> documentTypes = Stream.of(parentDocument, childDocument)
                .collect(toMap(doc -> doc.getFullName().toString(), identity()));
        Redundancy redundancy = createRedundancyWithGlobalDistributionValue(false);

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "The following document types are referenced from other documents, but are not globally distributed: 'parent'");
        new GlobalDistributionValidator()
                .validate(documentTypes, emptySet(), redundancy);
    }

    @Test
    public void validation_succeeds_if_referenced_document_is_global() {
        NewDocumentType parentDocument = new NewDocumentType(new NewDocumentType.Name("parent"));
        NewDocumentType childDocument = new NewDocumentType(
                new NewDocumentType.Name("child"), singleton(parentDocument.getFullName()));

        Map<String, NewDocumentType> documentTypes = Stream.of(parentDocument, childDocument)
                .collect(toMap(doc -> doc.getFullName().toString(), identity()));
        Set<NewDocumentType> globallyDistributedDocuments = singleton(parentDocument);
        Redundancy redundancy = createRedundancyWithGlobalDistributionValue(true);

        new GlobalDistributionValidator()
                .validate(documentTypes, globallyDistributedDocuments, redundancy);
    }

    @Test
    public void throws_exception_on_unknown_document() {
        NewDocumentType childDocument = new NewDocumentType(
                new NewDocumentType.Name("child"), singleton(new NewDocumentType.Name("unknown")));
        Map<String, NewDocumentType> documentTypes = singletonMap(childDocument.getName(), childDocument);
        Redundancy redundancy = createRedundancyWithGlobalDistributionValue(true);

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "The following document types are referenced from other documents, but are not listed in services.xml: 'unknown'");
        new GlobalDistributionValidator()
                .validate(documentTypes, emptySet(), redundancy);

    }

    private static Redundancy createRedundancyWithGlobalDistributionValue(boolean isGloballyDistributed) {
        Redundancy redundancy = mock(Redundancy.class);
        when(redundancy.isEffectivelyGloballyDistributed()).thenReturn(isGloballyDistributed);
        return redundancy;
    }
}
