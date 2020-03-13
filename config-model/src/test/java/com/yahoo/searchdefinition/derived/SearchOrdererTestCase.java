// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.searchdefinition.DocumentReference;
import com.yahoo.searchdefinition.DocumentReferences;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
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
public class SearchOrdererTestCase extends SearchDefinitionTestCase {

    private static Map<String, Search> createSearchDefinitions() {
        Map<String, Search> searchDefinitions = new HashMap<>();

        Search grandParent = createSearchDefinition("grandParent", searchDefinitions);

        Search mother = createSearchDefinition("mother", searchDefinitions);
        inherit(mother, grandParent);

        Search father = createSearchDefinition("father", searchDefinitions);
        inherit(father, grandParent);
        createDocumentReference(father, mother, "wife_ref");

        Search daugther = createSearchDefinition("daughter", searchDefinitions);
        inherit(daugther, father);
        inherit(daugther, mother);

        Search son = createSearchDefinition("son", searchDefinitions);
        inherit(son, father);
        inherit(son, mother);

        Search product = createSearchDefinition("product", searchDefinitions);

        Search pc = createSearchDefinition("pc", searchDefinitions);
        inherit(pc, product);
        Search pcAccessory = createSearchDefinition("accessory-pc", searchDefinitions);
        inherit(pcAccessory, product);
        createDocumentReference(pcAccessory, pc, "pc_ref");

        createSearchDefinition("alone", searchDefinitions);

        return searchDefinitions;
    }

    private static Search createSearchDefinition(String name, Map<String, Search> searchDefinitions) {
        Search search = new Search(name, null);
        SDDocumentType document = new SDDocumentType(name);
        document.setDocumentReferences(new DocumentReferences(emptyMap()));
        search.addDocument(document);
        searchDefinitions.put(search.getName(), search);
        return search;
    }

    private static void inherit(Search inheritee, Search inherited) {
        inheritee.getDocument().inherit(inherited.getDocument());
    }

    private static void assertOrder(List<String> expectedSearchOrder, List<String> inputNames) {
        Map<String, Search> searchDefinitions = createSearchDefinitions();
        List<Search> inputSearchDefinitions = inputNames.stream()
                .map(searchDefinitions::get)
                .map(Objects::requireNonNull)
                .collect(toList());
        List<String> actualSearchOrder = new SearchOrderer()
                .order(inputSearchDefinitions)
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
