// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.schema.DocumentReference;
import com.yahoo.schema.DocumentReferences;
import com.yahoo.schema.Schema;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.TemporarySDField;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyMap;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 * @author bjorncs
 */
public class SchemaOrdererTestCase extends AbstractSchemaTestCase {

    private static Map<String, Schema> createSchemas() {
        Map<String, Schema> schemas = new HashMap<>();

        Schema grandParent = createSchema("grandParent", schemas);

        Schema mother = createSchema("mother", schemas);
        inherit(mother, grandParent);

        Schema father = createSchema("father", schemas);
        inherit(father, grandParent);
        createDocumentReference(father, mother, "wife_ref");

        Schema daugther = createSchema("daughter", schemas);
        inherit(daugther, father);
        inherit(daugther, mother);

        Schema son = createSchema("son", schemas);
        inherit(son, father);
        inherit(son, mother);

        Schema product = createSchema("product", schemas);

        Schema pc = createSchema("pc", schemas);
        inherit(pc, product);
        Schema pcAccessory = createSchema("accessory-pc", schemas);
        inherit(pcAccessory, product);
        createDocumentReference(pcAccessory, pc, "pc_ref");

        createSchema("alone", schemas);

        return schemas;
    }

    private static Schema createSchema(String name, Map<String, Schema> schemas) {
        Schema schema = new Schema(name, MockApplicationPackage.createEmpty());
        SDDocumentType document = new SDDocumentType(name);
        document.setDocumentReferences(new DocumentReferences(emptyMap()));
        schema.addDocument(document);
        schemas.put(schema.getName(), schema);
        return schema;
    }

    private static void inherit(Schema inheritee, Schema inherited) {
        inheritee.getDocument().inherit(inherited.getDocument());
    }

    private static void assertOrder(List<String> expectedSearchOrder, List<String> inputNames) {
        inputNames.sort((a, b) -> a.compareTo(b));
        Map<String, Schema> schemas = createSchemas();
        List<Schema> inputSchemas = inputNames.stream()
                                              .map(schemas::get)
                                              .map(Objects::requireNonNull)
                                              .toList();
        List<String> actualSearchOrder = new SearchOrderer()
                .order(inputSchemas)
                .stream()
                .map(Schema::getName)
                .toList();
        assertEquals(expectedSearchOrder, actualSearchOrder);
    }

    @SuppressWarnings("deprecation")
    private static void createDocumentReference(Schema from, Schema to, String refFieldName) {
        SDDocumentType fromDocument = from.getDocument();
        SDField refField = new TemporarySDField(fromDocument, refFieldName, NewDocumentReferenceDataType.forDocumentName(to.getName()));
        fromDocument.addField(refField);
        Map<String, DocumentReference> originalMap = fromDocument.getDocumentReferences().get().referenceMap();
        HashMap<String, DocumentReference> modifiedMap = new HashMap<>(originalMap);
        modifiedMap.put(refFieldName, new DocumentReference(refField, to));
        fromDocument.setDocumentReferences(new DocumentReferences(modifiedMap));
    }


    @Test
    void testPerfectOrderingIsKept() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "son"),
                Arrays.asList("grandParent", "mother", "father", "daughter", "son", "product", "pc", "alone"));
    }

    @Test
    void testOneLevelReordering() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "son"),
                Arrays.asList("grandParent", "daughter", "son", "mother", "father", "pc", "product", "alone"));
    }

    @Test
    void testMultiLevelReordering() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "son"),
                Arrays.asList("daughter", "son", "mother", "father", "grandParent", "pc", "product", "alone"));
    }

    @Test
    void testAloneIsKeptInPlaceWithMultiLevelReordering() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "son"),
                Arrays.asList("alone", "daughter", "son", "mother", "father", "grandParent", "pc", "product"));
    }

    @Test
    void testPartialMultiLevelReordering() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "son"),
                Arrays.asList("daughter", "grandParent", "mother", "son", "father", "product", "pc", "alone"));
    }

    @Test
    void testMultilevelReorderingAccrossHierarchies() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "son"),
                Arrays.asList("daughter", "pc", "son", "mother", "grandParent", "father", "product", "alone"));
    }

    @Test
    void referees_are_ordered_before_referrer() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "accessory-pc", "son"),
                Arrays.asList("accessory-pc", "daughter", "pc", "son", "mother", "grandParent", "father", "product", "alone"));
    }


}
