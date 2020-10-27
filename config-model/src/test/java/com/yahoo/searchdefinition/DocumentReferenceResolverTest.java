// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.DataType;
import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import org.junit.Test;

import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class DocumentReferenceResolverTest {

    @Test
    public void reference_from_one_document_to_another_is_resolved() {
        // Create bar document with no fields
        Search barSearch = new Search();
        SDDocumentType barDocument = new SDDocumentType("bar", barSearch);
        barSearch.addDocument(barDocument);

        // Create foo document with document reference to bar and add another field
        SDField fooRefToBarField = new SDField
                ("bar_ref", ReferenceDataType.createWithInferredId(barDocument.getDocumentType()));
        AttributeUtils.addAttributeAspect(fooRefToBarField);
        SDField irrelevantField = new SDField("irrelevant_stuff", DataType.INT);
        Search fooSearch = new Search();
        SDDocumentType fooDocument = new SDDocumentType("foo", fooSearch);
        fooDocument.addField(fooRefToBarField);
        fooDocument.addField(irrelevantField);
        fooSearch.addDocument(fooDocument);

        DocumentReferenceResolver resolver = new DocumentReferenceResolver(asList(fooSearch, barSearch));
        resolver.resolveReferences(fooDocument);
        assertTrue(fooDocument.getDocumentReferences().isPresent());

        Map<String, DocumentReference> fooReferenceMap = fooDocument.getDocumentReferences().get().referenceMap();
        assertEquals(1, fooReferenceMap.size());
        assertSame(barSearch, fooReferenceMap.get("bar_ref").targetSearch());
        assertSame(fooRefToBarField, fooReferenceMap.get("bar_ref").referenceField());
    }

    @Test
    public void throws_user_friendly_exception_if_referenced_document_does_not_exist() {
        // Create foo document with document reference to non-existing document bar
        SDField fooRefToBarField = new SDField(
                "bar_ref", ReferenceDataType.createWithInferredId(TemporaryStructuredDataType.create("bar")));
        AttributeUtils.addAttributeAspect(fooRefToBarField);
        Search fooSearch = new Search();
        SDDocumentType fooDocument = new SDDocumentType("foo", fooSearch);
        fooDocument.addField(fooRefToBarField);
        fooSearch.addDocument(fooDocument);

        DocumentReferenceResolver resolver = new DocumentReferenceResolver(singletonList(fooSearch));
        Exception e = assertThrows(IllegalArgumentException.class, () -> resolver.resolveReferences(fooDocument));
        assertEquals("Invalid document reference 'bar_ref': Could not find document type 'bar'", e.getMessage());
    }

    @Test
    public void throws_exception_if_reference_is_not_an_attribute() {
        // Create bar document with no fields
        Search barSearch = new Search();
        SDDocumentType barDocument = new SDDocumentType("bar", barSearch);
        barSearch.addDocument(barDocument);

        // Create foo document with document reference to bar
        SDField fooRefToBarField = new SDField
                ("bar_ref", ReferenceDataType.createWithInferredId(barDocument.getDocumentType()));
        Search fooSearch = new Search();
        SDDocumentType fooDocument = new SDDocumentType("foo", fooSearch);
        fooDocument.addField(fooRefToBarField);
        fooSearch.addDocument(fooDocument);

        DocumentReferenceResolver resolver = new DocumentReferenceResolver(asList(fooSearch, barSearch));
        Exception e = assertThrows(IllegalArgumentException.class, () -> resolver.resolveReferences(fooDocument));
        assertEquals(        "The field 'bar_ref' is an invalid document reference. The field must be an attribute.", e.getMessage());

    }

}
