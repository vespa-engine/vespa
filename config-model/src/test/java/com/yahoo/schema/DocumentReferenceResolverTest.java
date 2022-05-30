// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.document.DataType;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class DocumentReferenceResolverTest {

    private static final String BAR = "bar";
    private static final String FOO = "foo";
    @SuppressWarnings("deprecation")
    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void reference_from_one_document_to_another_is_resolved() {
        // Create bar document with no fields
        Schema barSchema = new Schema(BAR, MockApplicationPackage.createEmpty());
        SDDocumentType barDocument = new SDDocumentType(BAR, barSchema);
        barSchema.addDocument(barDocument);

        // Create foo document with document reference to bar and add another field
        Schema fooSchema = new Schema(FOO, MockApplicationPackage.createEmpty());
        SDDocumentType fooDocument = new SDDocumentType("foo", fooSchema);
        SDField fooRefToBarField = new SDField
            (fooDocument, "bar_ref", new NewDocumentReferenceDataType(barDocument.getDocumentType()));
        AttributeUtils.addAttributeAspect(fooRefToBarField);
        SDField irrelevantField = new SDField(fooDocument, "irrelevant_stuff", DataType.INT);
        fooDocument.addField(fooRefToBarField);
        fooDocument.addField(irrelevantField);
        fooSchema.addDocument(fooDocument);

        DocumentReferenceResolver resolver = new DocumentReferenceResolver(asList(fooSchema, barSchema));
        resolver.resolveReferences(fooDocument);
        assertTrue(fooDocument.getDocumentReferences().isPresent());

        Map<String, DocumentReference> fooReferenceMap = fooDocument.getDocumentReferences().get().referenceMap();
        assertEquals(1, fooReferenceMap.size());
        assertSame(barSchema, fooReferenceMap.get("bar_ref").targetSearch());
        assertSame(fooRefToBarField, fooReferenceMap.get("bar_ref").referenceField());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void throws_user_friendly_exception_if_referenced_document_does_not_exist() {
        // Create foo document with document reference to non-existing document bar
        Schema fooSchema = new Schema(FOO, MockApplicationPackage.createEmpty());
        SDDocumentType fooDocument = new SDDocumentType("foo", fooSchema);
        SDField fooRefToBarField = new SDField(
                fooDocument,
                "bar_ref", NewDocumentReferenceDataType.forDocumentName("bar"));
        AttributeUtils.addAttributeAspect(fooRefToBarField);
        fooDocument.addField(fooRefToBarField);
        fooSchema.addDocument(fooDocument);

        DocumentReferenceResolver resolver = new DocumentReferenceResolver(singletonList(fooSchema));

        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "Invalid document reference 'bar_ref': Could not find document type 'bar'");
        resolver.resolveReferences(fooDocument);
    }

    @Test
    public void throws_exception_if_reference_is_not_an_attribute() {
        // Create bar document with no fields
        Schema barSchema = new Schema(BAR, MockApplicationPackage.createEmpty());
        SDDocumentType barDocument = new SDDocumentType("bar", barSchema);
        barSchema.addDocument(barDocument);

        // Create foo document with document reference to bar
        Schema fooSchema = new Schema(FOO, MockApplicationPackage.createEmpty());
        SDDocumentType fooDocument = new SDDocumentType("foo", fooSchema);
        SDField fooRefToBarField = new SDField
            (fooDocument, "bar_ref", new NewDocumentReferenceDataType(barDocument.getDocumentType()));
        fooDocument.addField(fooRefToBarField);
        fooSchema.addDocument(fooDocument);

        DocumentReferenceResolver resolver = new DocumentReferenceResolver(asList(fooSchema, barSchema));
        exceptionRule.expect(IllegalArgumentException.class);
        exceptionRule.expectMessage(
                "The field 'bar_ref' is an invalid document reference. The field must be an attribute.");
        resolver.resolveReferences(fooDocument);
    }

}
