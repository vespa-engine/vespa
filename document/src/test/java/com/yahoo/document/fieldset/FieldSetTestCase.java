// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.fieldset;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentTestCaseBase;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.FieldValue;
import org.junit.Test;

import java.util.Iterator;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test for field sets
 */
public class FieldSetTestCase extends DocumentTestCaseBase {

    @Test
    public void testClone() throws Exception {
        assertTrue(new AllFields().clone() instanceof AllFields);
        assertTrue(new DocumentOnly().clone() instanceof DocumentOnly);
        assertTrue(new NoFields().clone() instanceof NoFields);
        assertTrue(new DocIdOnly().clone() instanceof DocIdOnly);
    }

    @Test
    public void testParsing() {
        FieldSetRepo repo = new FieldSetRepo();

        assertTrue(repo.parse(docMan, AllFields.NAME) instanceof AllFields);
        assertTrue(repo.parse(docMan, DocumentOnly.NAME) instanceof DocumentOnly);
        assertTrue(repo.parse(docMan, NoFields.NAME) instanceof NoFields);
        assertTrue(repo.parse(docMan, DocIdOnly.NAME) instanceof DocIdOnly);

        FieldCollection collection = (FieldCollection)repo.parse(docMan, "testdoc:stringattr,intattr");
        assertEquals(2, collection.size());
    }

    void assertContains(String str1, String str2) throws Exception {
        FieldSetRepo repo = new FieldSetRepo();
        FieldSet set1 = repo.parse(docMan, str1);
        FieldSet set2 = repo.parse(docMan, str2);
        assertTrue(set1.clone().contains(set2.clone()));
    }

    void assertNotContains(String str1, String str2) throws Exception {
        FieldSetRepo repo = new FieldSetRepo();
        FieldSet set1 = repo.parse(docMan, str1);
        FieldSet set2 = repo.parse(docMan, str2);
        assertFalse(set1.clone().contains(set2.clone()));
    }

    void assertError(String str) {
        try {
            new FieldSetRepo().parse(docMan, str);
            fail();
        } catch (Exception e) {
        }
    }

    @Test
    public void testContains() throws Exception {
        Field intAttr = testDocType.getField("intattr");
        Field rawAttr = testDocType.getField("rawattr");

        assertFalse(intAttr.contains(testDocType.getField("byteattr")));
        assertTrue(intAttr.contains(testDocType.getField("intattr")));
        assertFalse(intAttr.contains(rawAttr));
        assertTrue(intAttr.contains(new DocIdOnly()));
        assertTrue(intAttr.contains(new NoFields()));
        assertFalse(intAttr.contains(new AllFields()));
        assertFalse(intAttr.contains(new DocumentOnly()));

        assertFalse(new NoFields().contains(intAttr));
        assertFalse(new NoFields().contains(new AllFields()));
        assertFalse(new NoFields().contains(new DocIdOnly()));
        assertFalse(new NoFields().contains(new DocumentOnly()));

        assertTrue(new AllFields().contains(intAttr));
        assertTrue(new AllFields().contains(rawAttr));
        assertTrue(new AllFields().contains(new DocIdOnly()));
        assertTrue(new AllFields().contains(new DocumentOnly()));
        assertTrue(new AllFields().contains(new NoFields()));
        assertTrue(new AllFields().contains(new AllFields()));

        assertTrue(new DocIdOnly().contains(new NoFields()));
        assertTrue(new DocIdOnly().contains(new DocIdOnly()));
        assertFalse(new DocIdOnly().contains(new DocumentOnly()));
        assertFalse(new DocIdOnly().contains(intAttr));

        assertTrue(new DocumentOnly().contains(new NoFields()));
        assertTrue(new DocumentOnly().contains(new DocIdOnly()));
        assertTrue(new DocumentOnly().contains(new DocumentOnly()));
        assertFalse(new DocumentOnly().contains(intAttr));

        assertContains("testdoc:rawattr,intattr", "testdoc:intattr");
        assertNotContains("testdoc:intattr", "testdoc:rawattr,intattr");
        assertContains("testdoc:intattr,rawattr", "testdoc:rawattr,intattr");

        assertError("nodoctype");
        assertError("unknowndoctype:foo");
        assertError("testdoc:unknownfield");
        assertError("[badid]");
    }

    String stringifyFields(Document doc) {
        StringBuilder retVal = new StringBuilder();
        for (Iterator<Map.Entry<Field, FieldValue>> i = doc.iterator(); i.hasNext(); ) {
            Map.Entry<Field, FieldValue> v = i.next();

            if (retVal.length() > 0) {
                retVal.append(",");
            }
            retVal.append(v.getKey().getName()).append(":").append(v.getValue().toString());
        }

        return retVal.toString();
    }

    String doCopyFields(Document source, String fieldSet) {
        FieldSetRepo repo = new FieldSetRepo();
        Document target = new Document(source.getDataType(), source.getId());
        repo.copyFields(source, target, repo.parse(docMan, fieldSet));
        return stringifyFields(target);
    }

    @Test
    public void testCopyDocumentFields() {
        Document doc = getTestDocument();
        doc.removeFieldValue("rawattr");

        assertEquals("floatattr:3.56,stringattr:tjohei,intattr:50,byteattr:30", doCopyFields(doc, AllFields.NAME));
        assertEquals("stringattr:tjohei,intattr:50", doCopyFields(doc, DocumentOnly.NAME));
        assertEquals("floatattr:3.56,byteattr:30", doCopyFields(doc, "testdoc:floatattr,byteattr"));
    }

    String doStripFields(Document source, String fieldSet) {
        FieldSetRepo repo = new FieldSetRepo();
        Document target = source.clone();
        repo.stripFields(target, repo.parse(docMan, fieldSet));
        return stringifyFields(target);
    }

    @Test
    public void testStripFields() {
        Document doc = getTestDocument();
        doc.removeFieldValue("rawattr");

        assertEquals("floatattr:3.56,stringattr:tjohei,intattr:50,byteattr:30", doStripFields(doc, AllFields.NAME));
        assertEquals("stringattr:tjohei,intattr:50", doStripFields(doc, DocumentOnly.NAME));
        assertEquals("floatattr:3.56,byteattr:30", doStripFields(doc, "testdoc:floatattr,byteattr"));
    }

    @Test
    public void testSerialize() {
        String fieldSets[] =
                {
                        AllFields.NAME,
                        NoFields.NAME,
                        DocIdOnly.NAME,
                        DocumentOnly.NAME,
                        "testdoc:rawattr",
                        "testdoc:rawattr,intattr"
                };

        FieldSetRepo repo = new FieldSetRepo();
        for (String fieldSet : fieldSets) {
            assertEquals(fieldSet, repo.serialize(repo.parse(docMan, fieldSet)));
        }
    }
}
