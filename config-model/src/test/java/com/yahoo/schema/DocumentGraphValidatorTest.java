// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.test.MockApplicationPackage;
import com.yahoo.documentmodel.NewDocumentReferenceDataType;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.document.TemporarySDField;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bjorncs
 */
public class DocumentGraphValidatorTest {

    @Test
    void simple_ref_dag_is_allowed() {
        Schema advertiserSchema = createSearchWithName("advertiser");
        Schema campaignSchema = createSearchWithName("campaign");
        Schema adSchema = createSearchWithName("ad");
        createDocumentReference(adSchema, advertiserSchema, "advertiser_ref");
        createDocumentReference(adSchema, campaignSchema, "campaign_ref");

        DocumentGraphValidator validator = new DocumentGraphValidator();
        validator.validateDocumentGraph(documentListOf(advertiserSchema, campaignSchema, adSchema));
    }

    @Test
    void simple_inheritance_dag_is_allowed() {
        Schema grandfather = createSearchWithName("grandfather");
        Schema father = createSearchWithName("father", grandfather);
        Schema son = createSearchWithName("son", father);

        DocumentGraphValidator validator = new DocumentGraphValidator();
        validator.validateDocumentGraph(documentListOf(son, father, grandfather));
    }

    @Test
    void complex_dag_is_allowed() {
        Schema grandfather = createSearchWithName("grandfather");
        Schema father = createSearchWithName("father", grandfather);
        Schema mother = createSearchWithName("mother", grandfather);
        createDocumentReference(father, mother, "wife_ref");
        Schema son = createSearchWithName("son", father, mother);
        Schema daughter = createSearchWithName("daughter", father, mother);
        createDocumentReference(daughter, son, "brother_ref");

        Schema randomGuy1 = createSearchWithName("randomguy1");
        Schema randomGuy2 = createSearchWithName("randomguy2");
        createDocumentReference(randomGuy1, mother, "secret_ref");

        DocumentGraphValidator validator = new DocumentGraphValidator();
        validator.validateDocumentGraph(documentListOf(son, father, grandfather, son, daughter, randomGuy1, randomGuy2));
    }

    @Test
    void ref_cycle_is_forbidden() {
        Throwable exception = assertThrows(DocumentGraphValidator.DocumentGraphException.class, () -> {
            Schema schema1 = createSearchWithName("doc1");
            Schema schema2 = createSearchWithName("doc2");
            Schema schema3 = createSearchWithName("doc3");
            createDocumentReference(schema1, schema2, "ref_2");
            createDocumentReference(schema2, schema3, "ref_3");
            createDocumentReference(schema3, schema1, "ref_1");

            DocumentGraphValidator validator = new DocumentGraphValidator();
            validator.validateDocumentGraph(documentListOf(schema1, schema2, schema3));
        });
        assertTrue(exception.getMessage().contains("Document dependency cycle detected: doc1->doc2->doc3->doc1."));
    }

    @Test
    void inherit_cycle_is_forbidden() {
        Throwable exception = assertThrows(DocumentGraphValidator.DocumentGraphException.class, () -> {
            Schema schema1 = createSearchWithName("doc1");
            Schema schema2 = createSearchWithName("doc2", schema1);
            Schema schema3 = createSearchWithName("doc3", schema2);
            schema1.getDocument().inherit(schema3.getDocument());

            DocumentGraphValidator validator = new DocumentGraphValidator();
            validator.validateDocumentGraph(documentListOf(schema1, schema2, schema3));
        });
        assertTrue(exception.getMessage().contains("Document dependency cycle detected: doc1->doc3->doc2->doc1."));
    }

    @Test
    void combined_inherit_and_ref_cycle_is_forbidden() {
        Throwable exception = assertThrows(DocumentGraphValidator.DocumentGraphException.class, () -> {
            Schema schema1 = createSearchWithName("doc1");
            Schema schema2 = createSearchWithName("doc2", schema1);
            Schema schema3 = createSearchWithName("doc3", schema2);
            createDocumentReference(schema1, schema3, "ref_1");

            DocumentGraphValidator validator = new DocumentGraphValidator();
            validator.validateDocumentGraph(documentListOf(schema1, schema2, schema3));
        });
        assertTrue(exception.getMessage().contains("Document dependency cycle detected: doc1->doc3->doc2->doc1."));
    }

    @Test
    void self_reference_is_forbidden() {
        Throwable exception = assertThrows(DocumentGraphValidator.DocumentGraphException.class, () -> {
            Schema adSchema = createSearchWithName("ad");
            createDocumentReference(adSchema, adSchema, "ad_ref");

            DocumentGraphValidator validator = new DocumentGraphValidator();
            validator.validateDocumentGraph(documentListOf(adSchema));
        });
        assertTrue(exception.getMessage().contains("Document dependency cycle detected: ad->ad."));
    }

    /**
     * Self inheritance is checked early because it is possible, and because it otherwise
     * produces a stack overflow before getting to graph validation.
     */
    @Test
    void self_inheritance_forbidden() {
        try {
            Schema adSchema = createSearchWithName("ad");
            SDDocumentType document = adSchema.getDocument();
            document.inherit(document);
            fail("Expected exception");
        }
        catch (IllegalArgumentException e) {
            assertEquals("Document type 'ad' cannot inherit itself", e.getMessage());
        }
    }

    private static List<SDDocumentType> documentListOf(Schema... schemas) {
        return Arrays.stream(schemas).map(Schema::getDocument).collect(toList());
    }

    private static Schema createSearchWithName(String name, Schema... parents) {
        Schema campaignSchema = new Schema(name, MockApplicationPackage.createEmpty());
        SDDocumentType document = new SDDocumentType(name);
        campaignSchema.addDocument(document);
        document.setDocumentReferences(new DocumentReferences(Collections.emptyMap()));
        Arrays.stream(parents)
                .map(Schema::getDocument)
                .forEach(document::inherit);
        return campaignSchema;
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
}
