// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.searchdefinition.DocumentReference;
import com.yahoo.searchdefinition.DocumentReferences;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SchemaTestCase;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.TemporarySDField;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 * @author bjorncs
 */
public class SearchOrdererTestCase extends SchemaTestCase {

    private static Map<String, Search> createSchemas() {
        Map<String, Search> schemas = new HashMap<>();

        Search grandParent = createSchema("grandParent", schemas);

        Search mother = createSchema("mother", schemas);
        inherit(mother, grandParent);

        Search father = createSchema("father", schemas);
        inherit(father, grandParent);
        createDocumentReference(father, mother, "wife_ref");

        Search daugther = createSchema("daughter", schemas);
        inherit(daugther, father);
        inherit(daugther, mother);

        Search son = createSchema("son", schemas);
        inherit(son, father);
        inherit(son, mother);

        Search product = createSchema("product", schemas);

        Search pc = createSchema("pc", schemas);
        inherit(pc, product);
        Search pcAccessory = createSchema("accessory-pc", schemas);
        inherit(pcAccessory, product);
        createDocumentReference(pcAccessory, pc, "pc_ref");

        createSchema("alone", schemas);

        return schemas;
    }

    private static Search createSchema(String name, Map<String, Search> schemas) {
        Search search = new Search(name, null);
        SDDocumentType document = new SDDocumentType(name);
        document.setDocumentReferences(new DocumentReferences(emptyMap()));
        search.addDocument(document);
        schemas.put(search.getName(), search);
        return search;
    }

    private static void inherit(Search inheritee, Search inherited) {
        inheritee.getDocument().inherit(inherited.getDocument());
    }

    private static void assertOrder(List<String> expectedSearchOrder, List<String> inputNames) {
        Map<String, Search> schemas = createSchemas();
        List<Search> inputSchemas = inputNames.stream()
                .map(schemas::get)
                .map(Objects::requireNonNull)
                .collect(toList());
        List<String> actualSearchOrder = new SearchOrderer()
                .order(inputSchemas)
                .stream()
                .map(Search::getName)
                .collect(toList());
        assertEquals(expectedSearchOrder, actualSearchOrder);
    }

    private static void createDocumentReference(Search from, Search to, String refFieldName) {
        SDField refField = new TemporarySDField(refFieldName, ReferenceDataType.createWithInferredId(TemporaryStructuredDataType.create(to.getName())));
        SDDocumentType fromDocument = from.getDocument();
        fromDocument.addField(refField);
        Map<String, DocumentReference> originalMap = fromDocument.getDocumentReferences().get().referenceMap();
        HashMap<String, DocumentReference> modifiedMap = new HashMap<>(originalMap);
        modifiedMap.put(refFieldName, new DocumentReference(refField, to));
        fromDocument.setDocumentReferences(new DocumentReferences(modifiedMap));
    }


    @Test
    public void testPerfectOrderingIsKept() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "son"),
                    Arrays.asList("grandParent", "mother", "father", "daughter", "son", "product", "pc", "alone"));
    }

    @Test
    public void testOneLevelReordering() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "son"),
                    Arrays.asList("grandParent", "daughter", "son", "mother", "father", "pc", "product", "alone"));
    }

    @Test
    public void testMultiLevelReordering() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "son"),
                    Arrays.asList("daughter", "son", "mother", "father", "grandParent", "pc", "product", "alone"));
    }

    @Test
    public void testAloneIsKeptInPlaceWithMultiLevelReordering() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "son"),
                    Arrays.asList("alone", "daughter", "son", "mother", "father", "grandParent", "pc", "product"));
    }

    @Test
    public void testPartialMultiLevelReordering() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "son"),
                    Arrays.asList("daughter", "grandParent", "mother", "son", "father", "product", "pc", "alone"));
    }

    @Test
    public void testMultilevelReorderingAccrossHierarchies() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "son"),
                    Arrays.asList("daughter", "pc", "son", "mother", "grandParent", "father", "product", "alone"));
    }

    @Test
    public void referees_are_ordered_before_referrer() {
        assertOrder(Arrays.asList("alone", "grandParent", "mother", "father", "daughter", "product", "pc", "accessory-pc", "son"),
                    Arrays.asList("accessory-pc", "daughter", "pc", "son", "mother", "grandParent", "father", "product", "alone"));
    }


}
