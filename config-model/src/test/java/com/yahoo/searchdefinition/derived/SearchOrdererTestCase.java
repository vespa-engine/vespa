// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.document.SDDocumentType;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
/**
 * @author bratseth
 */
public class SearchOrdererTestCase extends SearchDefinitionTestCase {

    private Map<String, Search> createSearchDefinitions() {
        Map<String, Search> searchDefinitions = new HashMap<>();

        Search grandParent = createSearchDefinition("grandParent", searchDefinitions);

        Search mother = createSearchDefinition("mother", searchDefinitions);
        inherit(mother, grandParent);

        Search father = createSearchDefinition("father", searchDefinitions);
        inherit(father, grandParent);

        Search daugther = createSearchDefinition("daughter", searchDefinitions);
        inherit(daugther, father);
        inherit(daugther, mother);

        Search son = createSearchDefinition("son", searchDefinitions);
        inherit(son, father);
        inherit(son, mother);

        Search product = createSearchDefinition("product", searchDefinitions);

        Search pc = createSearchDefinition("pc", searchDefinitions);
        inherit(pc, product);

        createSearchDefinition("alone", searchDefinitions);

        return searchDefinitions;
    }

    private Search createSearchDefinition(String name, Map<String, Search> searchDefinitions) {
        Search search = new Search(name, null);
        SDDocumentType document = new SDDocumentType(name);
        search.addDocument(document);
        searchDefinitions.put(search.getName(), search);
        return search;
    }

    private void inherit(Search inheritee, Search inherited) {
        inheritee.getDocument().inherit(inherited.getDocument());
    }

    private void assertOrder(List<String> rightOrder, List<String> input) {
        Map<String, Search> searchDefinitions = createSearchDefinitions();
        SearchOrderer orderer = new SearchOrderer();
        List<Search> unordered = new ArrayList<>();
        for (String anInput : input) {
            Search search = searchDefinitions.get(anInput);
            assertNotNull(anInput + " exists", search);
            unordered.add(search);
        }
        List<Search> ordered = orderer.order(unordered);
        List<String> names = new LinkedList<>();
        for (int i = 0; i < rightOrder.size(); i++) {
            Search search = ordered.get(i);
            names.add(search.getName());
        }
        assertEquals(rightOrder.toString(), names.toString());
    }

    @Test
    public void testPerfectOrderingIsKept() {
        assertOrder(Arrays.asList("alone", "grandParent", "father", "mother", "daughter", "product", "pc", "son"),
                    Arrays.asList("grandParent", "mother", "father", "daughter", "son", "product", "pc", "alone"));
    }
    @Test
    public void testOneLevelReordering() {
        assertOrder(Arrays.asList("alone", "grandParent", "father", "mother", "daughter", "product", "pc", "son"),
                    Arrays.asList("grandParent", "daughter", "son", "mother", "father", "pc", "product", "alone"));
    }
    @Test
    public void testMultiLevelReordering() {
        assertOrder(Arrays.asList("alone", "grandParent", "father", "mother", "daughter", "product", "pc", "son"),
                    Arrays.asList("daughter", "son", "mother", "father", "grandParent", "pc", "product", "alone"));
    }
    @Test
    public void testAloneIsKeptInPlaceWithMultiLevelReordering() {
        assertOrder(Arrays.asList("alone", "grandParent", "father", "mother", "daughter", "product", "pc", "son"),
                    Arrays.asList("alone", "daughter", "son", "mother", "father", "grandParent", "pc", "product"));
    }
    @Test
    public void testPartialMultiLevelReordering() {
        assertOrder(Arrays.asList("alone", "grandParent", "father", "mother", "daughter", "product", "pc", "son"),
                    Arrays.asList("daughter", "grandParent", "mother", "son", "father", "product", "pc", "alone"));
    }
    @Test
    public void testMultilevelReorderingAccrossHierarchies() {
        assertOrder(Arrays.asList("alone", "grandParent", "father", "mother", "daughter", "product", "pc", "son"),
                    Arrays.asList("daughter", "pc", "son", "mother", "grandParent", "father", "product", "alone"));
    }


}
