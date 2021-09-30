// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.document.ReferenceDataType;
import com.yahoo.document.TemporaryStructuredDataType;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import com.yahoo.searchdefinition.document.TemporarySDField;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * @author bjorncs
 */
public class DocumentGraphValidatorTest {

    @Rule
    public final ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void simple_ref_dag_is_allowed() {
        Search advertiserSearch = createSearchWithName("advertiser");
        Search campaignSearch = createSearchWithName("campaign");
        Search adSearch = createSearchWithName("ad");
        createDocumentReference(adSearch, advertiserSearch, "advertiser_ref");
        createDocumentReference(adSearch, campaignSearch, "campaign_ref");

        DocumentGraphValidator validator = new DocumentGraphValidator();
        validator.validateDocumentGraph(documentListOf(advertiserSearch, campaignSearch, adSearch));
    }

    @Test
    public void simple_inheritance_dag_is_allowed() {
        Search grandfather = createSearchWithName("grandfather");
        Search father = createSearchWithName("father", grandfather);
        Search son = createSearchWithName("son", father);

        DocumentGraphValidator validator = new DocumentGraphValidator();
        validator.validateDocumentGraph(documentListOf(son, father, grandfather));
    }

    @Test
    public void complex_dag_is_allowed() {
        Search grandfather = createSearchWithName("grandfather");
        Search father = createSearchWithName("father", grandfather);
        Search mother = createSearchWithName("mother", grandfather);
        createDocumentReference(father, mother, "wife_ref");
        Search son = createSearchWithName("son", father, mother);
        Search daughter = createSearchWithName("daughter", father, mother);
        createDocumentReference(daughter, son, "brother_ref");

        Search randomGuy1 = createSearchWithName("randomguy1");
        Search randomGuy2 = createSearchWithName("randomguy2");
        createDocumentReference(randomGuy1, mother, "secret_ref");

        DocumentGraphValidator validator = new DocumentGraphValidator();
        validator.validateDocumentGraph(documentListOf(son, father, grandfather, son, daughter, randomGuy1, randomGuy2));
    }

    @Test
    public void ref_cycle_is_forbidden() {
        Search search1 = createSearchWithName("doc1");
        Search search2 = createSearchWithName("doc2");
        Search search3 = createSearchWithName("doc3");
        createDocumentReference(search1, search2, "ref_2");
        createDocumentReference(search2, search3, "ref_3");
        createDocumentReference(search3, search1, "ref_1");

        DocumentGraphValidator validator = new DocumentGraphValidator();
        exceptionRule.expect(DocumentGraphValidator.DocumentGraphException.class);
        exceptionRule.expectMessage("Document dependency cycle detected: doc1->doc2->doc3->doc1.");
        validator.validateDocumentGraph(documentListOf(search1, search2, search3));
    }

    @Test
    public void inherit_cycle_is_forbidden() {
        Search search1 = createSearchWithName("doc1");
        Search search2 = createSearchWithName("doc2", search1);
        Search search3 = createSearchWithName("doc3", search2);
        search1.getDocument().inherit(search3.getDocument());

        DocumentGraphValidator validator = new DocumentGraphValidator();
        exceptionRule.expect(DocumentGraphValidator.DocumentGraphException.class);
        exceptionRule.expectMessage("Document dependency cycle detected: doc1->doc3->doc2->doc1.");
        validator.validateDocumentGraph(documentListOf(search1, search2, search3));
    }

    @Test
    public void combined_inherit_and_ref_cycle_is_forbidden() {
        Search search1 = createSearchWithName("doc1");
        Search search2 = createSearchWithName("doc2", search1);
        Search search3 = createSearchWithName("doc3", search2);
        createDocumentReference(search1, search3, "ref_1");

        DocumentGraphValidator validator = new DocumentGraphValidator();
        exceptionRule.expect(DocumentGraphValidator.DocumentGraphException.class);
        exceptionRule.expectMessage("Document dependency cycle detected: doc1->doc3->doc2->doc1.");
        validator.validateDocumentGraph(documentListOf(search1, search2, search3));
    }

    @Test
    public void self_reference_is_forbidden() {
        Search adSearch = createSearchWithName("ad");
        createDocumentReference(adSearch, adSearch, "ad_ref");

        DocumentGraphValidator validator = new DocumentGraphValidator();
        exceptionRule.expect(DocumentGraphValidator.DocumentGraphException.class);
        exceptionRule.expectMessage("Document dependency cycle detected: ad->ad.");
        validator.validateDocumentGraph(documentListOf(adSearch));
    }

    @Test
    public void self_inheritance_forbidden() {
        Search adSearch = createSearchWithName("ad");
        SDDocumentType document = adSearch.getDocument();
        document.inherit(document);

        DocumentGraphValidator validator = new DocumentGraphValidator();
        exceptionRule.expect(DocumentGraphValidator.DocumentGraphException.class);
        exceptionRule.expectMessage("Document dependency cycle detected: ad->ad.");
        validator.validateDocumentGraph(documentListOf(adSearch));
    }

    private static List<SDDocumentType> documentListOf(Search... searches) {
        return Arrays.stream(searches).map(Search::getDocument).collect(toList());
    }

    private static Search createSearchWithName(String name, Search... parents) {
        Search campaignSearch = new Search(name);
        SDDocumentType document = new SDDocumentType(name);
        campaignSearch.addDocument(document);
        document.setDocumentReferences(new DocumentReferences(Collections.emptyMap()));
        Arrays.stream(parents)
                .map(Search::getDocument)
                .forEach(document::inherit);
        return campaignSearch;
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
}
