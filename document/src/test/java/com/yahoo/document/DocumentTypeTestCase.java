// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import org.junit.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Thomas Gundersen
 * @author bratseth
 */
public class DocumentTypeTestCase {

    // Verify that we can register and retrieve fields.
    @Test
    public void testSetGet() {
        DocumentType docType = new DocumentType("testdoc");
        docType.addField("Bongle", DataType.STRING);
        docType.addField("nalle", DataType.INT);

        assertEquals(docType.getField("Bongle").getName(), "Bongle");
        assertNull(docType.getField("bongle"));

    }

    @Test
    public void testFieldSets() {
        DocumentType root = new DocumentType("root");
        root.addField("rootfield1", DataType.STRING);
        root.addField("rootfield2", DataType.STRING);
        root.addField("rootfield3", DataType.STRING);
        root.addFieldSets(Map.of(DocumentType.DOCUMENT, List.of("rootfield2")));
        assertEquals(1, root.fieldSet().size());
        assertEquals("rootfield2", root.fieldSet().iterator().next().getName());
        assertEquals(3, root.fieldSetAll().size());
    }

    @Test
    public void testInheritance() {
        DocumentTypeManager typeManager = new DocumentTypeManager();

        DocumentType root = new DocumentType("root");
        root.addField("rootfield", DataType.STRING);
        root.addFieldSets(Map.of(DocumentType.DOCUMENT, List.of("rootfield")));

        DocumentType parent1 = new DocumentType("parent1");
        parent1.addField("overridden", DataType.STRING);
        parent1.addField("parent1field", DataType.STRING);
        parent1.inherit(root);
        parent1.addFieldSets(Map.of(DocumentType.DOCUMENT, List.of("parent1field", "overridden")));

        DocumentType parent2 = new DocumentType("parent2");
        parent2.addField("parent2field", DataType.STRING);
        parent2.inherit(root);
        parent2.addFieldSets(Map.of(DocumentType.DOCUMENT, List.of("parent2field")));

        DocumentType child = new DocumentType("child");
        child.addField("childfield", DataType.INT);
        child.addField("overridden", DataType.STRING);
        child.inherit(parent1);
        child.inherit(parent2);
        child.addFieldSets(Map.of(DocumentType.DOCUMENT, List.of("childfield", "overridden")));

        typeManager.register(root);
        typeManager.register(parent1);
        typeManager.register(parent2);
        typeManager.register(child);

        Iterator inherited = child.getInheritedTypes().iterator();
        assertEquals(parent1, inherited.next());
        assertEquals(parent2, inherited.next());
        assertFalse(inherited.hasNext());

        inherited = parent1.getInheritedTypes().iterator();
        assertEquals(root, inherited.next());
        assertFalse(inherited.hasNext());

        inherited = parent2.getInheritedTypes().iterator();
        assertEquals(root, inherited.next());
        assertFalse(inherited.hasNext());

        inherited = root.getInheritedTypes().iterator();
        assertEquals(DataType.DOCUMENT, inherited.next());
        assertFalse(inherited.hasNext());

        Iterator fields = child.fieldSet().iterator();
        Field field;

        field = (Field) fields.next();
        assertEquals("rootfield", field.getName());

        field = (Field) fields.next();
        assertEquals("overridden", field.getName());
        assertEquals(DataType.STRING, field.getDataType());

        field = (Field) fields.next();
        assertEquals("parent1field", field.getName());

        field = (Field) fields.next();
        assertEquals("parent2field", field.getName());

        field = (Field) fields.next();
        assertEquals("childfield", field.getName());

        assertFalse(fields.hasNext());

        assertNotNull(child.getField("rootfield"));
    }

}
