// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

/**
 * Test for field sets
 */
public class FieldSetTestCase extends DocumentTestCaseBase {

    @Test
    @SuppressWarnings("deprecation")
    public void testClone() throws Exception {
        assertTrue(new AllFields().clone() instanceof AllFields);
        assertTrue(new NoFields().clone() instanceof NoFields);
        assertTrue(new HeaderFields().clone() instanceof HeaderFields);
        assertTrue(new BodyFields().clone() instanceof BodyFields);
        assertTrue(new DocIdOnly().clone() instanceof DocIdOnly);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testParsing() {
        FieldSetRepo repo = new FieldSetRepo();

        assertTrue(repo.parse(docMan, "[all]") instanceof AllFields);
        assertTrue(repo.parse(docMan, "[none]") instanceof NoFields);
        assertTrue(repo.parse(docMan, "[id]") instanceof DocIdOnly);
        assertTrue(repo.parse(docMan, "[header]") instanceof HeaderFields);
        assertTrue(repo.parse(docMan, "[body]") instanceof BodyFields);

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
            assertTrue(false);
        } catch (Exception e) {
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    public void testContains() throws Exception {
        Field headerField = testDocType.getField("intattr");
        Field bodyField = testDocType.getField("rawattr");

        assertFalse(headerField.contains(testDocType.getField("byteattr")));
        assertTrue(headerField.contains(testDocType.getField("intattr")));
        assertFalse(headerField.contains(bodyField));
        assertTrue(headerField.contains(new DocIdOnly()));
        assertTrue(headerField.contains(new NoFields()));
        assertFalse(headerField.contains(new AllFields()));
        assertFalse(headerField.contains(new HeaderFields()));
        assertFalse(headerField.contains(new BodyFields()));

        assertFalse(new NoFields().contains(headerField));
        assertFalse(new NoFields().contains(new AllFields()));
        assertFalse(new NoFields().contains(new DocIdOnly()));

        assertTrue(new AllFields().contains(new HeaderFields()));
        assertTrue(new AllFields().contains(headerField));
        assertTrue(new AllFields().contains(bodyField));
        assertTrue(new AllFields().contains(new BodyFields()));
        assertTrue(new AllFields().contains(new DocIdOnly()));
        assertTrue(new AllFields().contains(new NoFields()));
        assertTrue(new AllFields().contains(new AllFields()));

        assertTrue(new DocIdOnly().contains(new NoFields()));
        assertTrue(new DocIdOnly().contains(new DocIdOnly()));
        assertFalse(new DocIdOnly().contains(headerField));

        assertTrue(new HeaderFields().contains(headerField));
        assertTrue(new HeaderFields().contains(bodyField));
        assertTrue(new HeaderFields().contains(new DocIdOnly()));
        assertTrue(new HeaderFields().contains(new NoFields()));

        assertNotContains("[body]", "testdoc:rawattr");
        assertContains("[header]", "testdoc:intattr");
        assertContains("[header]", "testdoc:rawattr");
        assertContains("testdoc:rawattr,intattr", "testdoc:intattr");
        assertNotContains("testdoc:intattr", "testdoc:rawattr,intattr");
        assertContains("testdoc:intattr,rawattr", "testdoc:rawattr,intattr");

        assertError("nodoctype");
        assertError("unknowndoctype:foo");
        assertError("testdoc:unknownfield");
        assertError("[badid]");
    }

    String stringifyFields(Document doc) {
        String retVal = "";
        for (Iterator<Map.Entry<Field, FieldValue>> i = doc.iterator(); i.hasNext(); ) {
            Map.Entry<Field, FieldValue> v = i.next();

            if (retVal.length() > 0) {
                retVal += ",";
            }
            retVal += v.getKey().getName() + ":" + v.getValue().toString();
        }

        return retVal;
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

        assertEquals("", doCopyFields(doc, "[body]"));
        assertEquals("floatattr:3.56,stringattr:tjohei,intattr:50,byteattr:30", doCopyFields(doc, "[header]"));
        assertEquals("floatattr:3.56,stringattr:tjohei,intattr:50,byteattr:30", doCopyFields(doc, "[all]"));
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

        assertEquals("", doStripFields(doc, "[body]"));
        assertEquals("floatattr:3.56,stringattr:tjohei,intattr:50,byteattr:30", doStripFields(doc, "[header]"));
        assertEquals("floatattr:3.56,stringattr:tjohei,intattr:50,byteattr:30", doStripFields(doc, "[all]"));
        assertEquals("floatattr:3.56,byteattr:30", doStripFields(doc, "testdoc:floatattr,byteattr"));
    }

    @Test
    public void testSerialize() {
        String fieldSets[] =
                {
                        "[all]",
                        "[none]",
                        "[header]",
                        "[docid]",
                        "[body]",
                        "testdoc:rawattr",
                        "testdoc:rawattr,intattr"
                };

        FieldSetRepo repo = new FieldSetRepo();
        for (String fieldSet : fieldSets) {
            assertEquals(fieldSet, repo.serialize(repo.parse(docMan, fieldSet)));
        }
    }
}
