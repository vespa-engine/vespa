// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import java.util.Iterator;

/**
 * @author Thomas Gundersen
 * @author bratseth
 */
public class DocumentTypeTestCase extends junit.framework.TestCase {

    public DocumentTypeTestCase(String name) {
        super(name);
    }

    // Verify that we can register and retrieve fields.
    public void testSetGet() {
        DocumentType docType = new DocumentType("testdoc");
        docType.addField("Bongle", DataType.STRING);
        docType.addField("nalle", DataType.INT);

        assertEquals(docType.getField("Bongle").getName(), "Bongle");
        assertNull(docType.getField("bongle"));

    }

    public void testInheritance() {
        DocumentTypeManager typeManager = new DocumentTypeManager();

        DocumentType child = new DocumentType("child");
        Iterator inherited;

        child.addField("childfield", DataType.INT);
        child.addField("overridden", DataType.STRING);

        DocumentType parent1 = new DocumentType("parent1");
        parent1.addField("overridden", DataType.STRING);
        parent1.addField("parent1field", DataType.STRING);
        child.inherit(parent1);

        DocumentType parent2 = new DocumentType("parent2");
        parent2.addField("parent2field", DataType.STRING);
        child.inherit(parent2);

        DocumentType root = new DocumentType("root");
        root.addField("rootfield", DataType.STRING);
        parent1.inherit(root);
        parent2.inherit(root);

        typeManager.register(root);
        typeManager.register(parent1);
        typeManager.register(parent2);
        typeManager.register(child);

        inherited = child.getInheritedTypes().iterator();
        assertEquals(parent1, inherited.next());
        assertEquals(parent2, inherited.next());
        assertTrue(!inherited.hasNext());

        inherited = parent1.getInheritedTypes().iterator();
        assertEquals(root, inherited.next());
        assertTrue(!inherited.hasNext());

        inherited = parent2.getInheritedTypes().iterator();
        assertEquals(root, inherited.next());
        assertTrue(!inherited.hasNext());

        inherited = root.getInheritedTypes().iterator();
        assertEquals(DataType.DOCUMENT, inherited.next());
        assertTrue(!inherited.hasNext());

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

        assert(child.getField("rootfield") != null);

        // TODO: Test uninheriting
    }

}
