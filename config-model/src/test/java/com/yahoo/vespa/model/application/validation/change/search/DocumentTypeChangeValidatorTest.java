// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.application.validation.change.search;

import com.yahoo.config.application.api.ValidationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.document.DocumentType;
import com.yahoo.document.Field;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.schema.FieldSets;
import com.yahoo.vespa.model.application.validation.change.VespaConfigChangeAction;
import com.yahoo.vespa.model.application.validation.change.VespaRefeedAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.model.application.validation.change.ConfigChangeTestUtils.newRefeedAction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test validation of changes between a current and next document type used in a document database.
 *
 * @author toregge
 */
public class DocumentTypeChangeValidatorTest {

    private static class Fixture extends ContentClusterFixture {

        DocumentTypeChangeValidator validator;

        public Fixture(String currentSd, String nextSd) throws Exception {
            super(currentSd, nextSd);
            validator = new DocumentTypeChangeValidator(ClusterSpec.Id.from("test"),
                                                        currentDocType(),
                                                        nextDocType());
        }

        @Override
        public List<VespaConfigChangeAction> validate() {
            return validator.validate();
        }

    }

    @Test
    void requireThatFieldRemovalIsOK() throws Exception {
        Fixture f = new Fixture("field f1 type string { indexing: summary }",
                "field f2 type string { indexing: summary }");
        f.assertValidation();
    }

    @Test
    void requireThatSameDataTypeIsOK() throws Exception {
        Fixture f = new Fixture("field f1 type string { indexing: summary }",
                "field f1 type string { indexing: summary }");
        f.assertValidation();
    }

    @Test
    void requireThatDataTypeChangeIsNotOK() throws Exception {
        Fixture f = new Fixture("field f1 type string { indexing: summary }",
                "field f1 type int { indexing: summary }");
        Instant.now();
        f.assertValidation(newRefeedAction(ClusterSpec.Id.from("test"), ValidationId.fieldTypeChange, "Field 'f1' changed: data type: 'string' -> 'int'"));
    }

    @Test
    void requireThatAddingCollectionTypeIsNotOK() throws Exception {
        Fixture f = new Fixture("field f1 type string { indexing: summary }",
                "field f1 type array<string> { indexing: summary }");
        Instant.now();
        f.assertValidation(newRefeedAction(ClusterSpec.Id.from("test"), ValidationId.fieldTypeChange, "Field 'f1' changed: data type: 'string' -> 'Array<string>'"));
    }


    @Test
    void requireThatSameNestedDataTypeIsOK() throws Exception {
        Fixture f = new Fixture("field f1 type array<string> { indexing: summary }",
                "field f1 type array<string> { indexing: summary }");
        f.assertValidation();
    }

    @Test
    void requireThatNestedDataTypeChangeIsNotOK() throws Exception {
        Fixture f = new Fixture("field f1 type array<string> { indexing: summary }",
                "field f1 type array<int> { indexing: summary }");
        Instant.now();
        f.assertValidation(newRefeedAction(ClusterSpec.Id.from("test"), ValidationId.fieldTypeChange, "Field 'f1' changed: data type: 'Array<string>' -> 'Array<int>'"));
    }

    @Test
    void requireThatChangedCollectionTypeIsNotOK() throws Exception {
        Fixture f = new Fixture("field f1 type array<string> { indexing: summary }",
                "field f1 type weightedset<string> { indexing: summary }");
        Instant.now();
        f.assertValidation(newRefeedAction(ClusterSpec.Id.from("test"), ValidationId.fieldTypeChange, "Field 'f1' changed: data type: 'Array<string>' -> 'WeightedSet<string>'"));
    }

    @Test
    void requireThatMultipleDataTypeChangesIsNotOK() throws Exception {
        Fixture f = new Fixture("field f1 type string { indexing: summary } field f2 type int { indexing: summary }",
                "field f2 type string { indexing: summary } field f1 type int { indexing: summary }");
        Instant.now();
        Instant.now();
        f.assertValidation(Arrays.asList(newRefeedAction(ClusterSpec.Id.from("test"), ValidationId.fieldTypeChange, "Field 'f1' changed: data type: 'string' -> 'int'"),
                newRefeedAction(ClusterSpec.Id.from("test"), ValidationId.fieldTypeChange, "Field 'f2' changed: data type: 'int' -> 'string'")));
    }

    @Test
    void requireThatSameDataTypeInStructFieldIsOK() throws Exception {
        Fixture f = new Fixture("struct s1 { field f1 type string {} } field f2 type s1 { indexing: summary }",
                "struct s1 { field f1 type string {} } field f2 type s1 { indexing: summary }");
        f.assertValidation();
    }

    @Test
    void requireThatSameNestedDataTypeChangeInStructFieldIsOK() throws Exception {
        Fixture f = new Fixture("struct s1 { field f1 type array<string> {} } field f2 type s1 { indexing: summary }",
                "struct s1 { field f1 type array<string> {} } field f2 type s1 { indexing: summary }");
        f.assertValidation();
    }

    @Test
    void requireThatAddingFieldInStructFieldIsOK() throws Exception {
        Fixture f = new Fixture("struct s1 { field f1 type string {} } field f3 type s1 { indexing: summary }",
                "struct s1 { field f1 type string {} field f2 type int {} } field f3 type s1 { indexing: summary }");
        f.assertValidation();
    }

    @Test
    void requireThatRemovingFieldInStructFieldIsOK() throws Exception {
        Fixture f = new Fixture("struct s1 { field f1 type string {} field f2 type int {} } field f3 type s1 { indexing: summary }",
                "struct s1 { field f1 type string {} } field f3 type s1 { indexing: summary }");
        f.assertValidation();
    }

    @Test
    void requireThatDataTypeChangeInStructFieldIsNotOK() throws Exception {
        Fixture f = new Fixture("struct s1 { field f1 type string {} } field f2 type s1 { indexing: summary }",
                "struct s1 { field f1 type int {} } field f2 type s1 { indexing: summary }");
        Instant.now();
        f.assertValidation(newRefeedAction(ClusterSpec.Id.from("test"), ValidationId.fieldTypeChange, "Field 'f2' changed: data type: 's1:{f1:string}' -> 's1:{f1:int}'"));
    }

    @Test
    void requireThatNestedDataTypeChangeInStructFieldIsNotOK() throws Exception {
        Fixture f = new Fixture("struct s1 { field f1 type array<string> {} } field f2 type s1 { indexing: summary }",
                "struct s1 { field f1 type array<int> {} } field f2 type s1 { indexing: summary }");
        Instant.now();
        f.assertValidation(newRefeedAction(ClusterSpec.Id.from("test"), ValidationId.fieldTypeChange, "Field 'f2' changed: data type: 's1:{f1:Array<string>}' -> 's1:{f1:Array<int>}'"));
    }

    @Test
    void requireThatDataTypeChangeInNestedStructFieldIsNotOK() throws Exception {
        Fixture f = new Fixture("struct s1 { field f1 type string {} } struct s2 { field f2 type s1 {} } field f3 type s2 { indexing: summary }",
                "struct s1 { field f1 type int {} }    struct s2 { field f2 type s1 {} } field f3 type s2 { indexing: summary }");
        Instant.now();
        f.assertValidation(newRefeedAction(ClusterSpec.Id.from("test"), ValidationId.fieldTypeChange, "Field 'f3' changed: data type: 's2:{s1:{f1:string}}' -> 's2:{s1:{f1:int}}'"));
    }

    @Test
    void requireThatMultipleDataTypeChangesInStructFieldIsNotOK() throws Exception {
        Fixture f = new Fixture("struct s1 { field f1 type string {} field f2 type int {} } field f3 type s1 { indexing: summary }",
                "struct s1 { field f1 type int {} field f2 type string {} } field f3 type s1 { indexing: summary }");
        Instant.now();
        f.assertValidation(newRefeedAction(ClusterSpec.Id.from("test"), ValidationId.fieldTypeChange, "Field 'f3' changed: data type: 's1:{f1:string,f2:int}' -> 's1:{f1:int,f2:string}'"));
    }

    @Test
    void requireThatChangingTargetTypeOfReferenceFieldIsNotOK() {
        var validator = new DocumentTypeChangeValidator(ClusterSpec.Id.from("test"),
                createDocumentTypeWithReferenceField("oldDoc"),
                createDocumentTypeWithReferenceField("newDoc"));
        List<VespaConfigChangeAction> result = validator.validate();
        assertEquals(1, result.size());
        VespaConfigChangeAction action = result.get(0);
        assertTrue(action instanceof VespaRefeedAction);
        assertEquals(
                "type='refeed', " +
                        "message='Field 'ref' changed: data type: 'Reference<oldDoc>' -> 'Reference<newDoc>'', " +
                        "services=[], documentType=''",
                action.toString());
    }

    @Test
    void changing_tensor_type_of_tensor_field_requires_refeed() throws Exception {
        Instant.now();
        new Fixture(
                "field f1 type tensor(x[2]) { indexing: attribute }",
                "field f1 type tensor(x[3]) { indexing: attribute }")
                .assertValidation(newRefeedAction(ClusterSpec.Id.from("test"), ValidationId.fieldTypeChange, "Field 'f1' changed: data type: 'tensor(x[2])' -> 'tensor(x[3])'"));

        Instant.now();
        new Fixture(
                "field f1 type tensor(x[5]) { indexing: attribute }",
                "field f1 type tensor(x[3]) { indexing: attribute }")
                .assertValidation(newRefeedAction(ClusterSpec.Id.from("test"), ValidationId.fieldTypeChange, "Field 'f1' changed: data type: 'tensor(x[5])' -> 'tensor(x[3])'"));
    }

    private static NewDocumentType createDocumentTypeWithReferenceField(String nameReferencedDocumentType) {
        StructDataType headerfields = new StructDataType("headerfields");
        headerfields.addField(new Field("ref", new NewDocumentReferenceDataType(new DocumentType(nameReferencedDocumentType))));
        return new NewDocumentType(
                new NewDocumentType.Name("mydoc"),
                headerfields,
                new FieldSets(Optional.empty()),
                Collections.emptySet(),
                Collections.emptySet());
    }

}
