// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.test;

import com.yahoo.document.DataType;
import com.yahoo.document.DataTypeName;
import com.yahoo.documentmodel.VespaDocumentType;
import com.yahoo.searchdefinition.SchemaTestCase;
import com.yahoo.searchdefinition.document.SDDocumentType;
import com.yahoo.searchdefinition.document.SDField;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.*;

/**
 * @author Thomas Gundersen
 * @author bratseth
 */
public class SDDocumentTypeTestCase extends SchemaTestCase {

    // Verify that we can register and retrieve fields.
    @Test
    public void testSetGet() {
        SDDocumentType docType = new SDDocumentType("testdoc");
        docType.addField("Bongle", DataType.STRING);
        docType.addField("nalle", DataType.INT);

        assertNotNull(docType.getField("Bongle").getName());
        assertNull(docType.getField("bongle"));

    }

    @Test
    public void testInheritance() {
        SDDocumentType child = new SDDocumentType("child");
        Iterator<SDDocumentType> inherited = child.getInheritedTypes().iterator();
        assertTrue(inherited.hasNext());
        assertEquals(inherited.next().getDocumentName(), VespaDocumentType.NAME);
        assertFalse(inherited.hasNext());

        child.addField("childfield", DataType.INT);
        SDField overridden = child.addField("overridden", DataType.STRING);

        SDDocumentType parent1 = new SDDocumentType("parent1");
        SDField overridden2 = parent1.addField("overridden", DataType.STRING);
        parent1.addField("parent1field", DataType.STRING);
        child.inherit(parent1);

        SDDocumentType parent2 = new SDDocumentType("parent2");
        parent2.addField("parent2field", DataType.STRING);
        child.inherit(parent2);

        SDDocumentType root = new SDDocumentType("root");
        root.addField("rootfield", DataType.STRING);
        parent1.inherit(root);
        parent2.inherit(root);

        inherited = child.getInheritedTypes().iterator();
        assertEquals(VespaDocumentType.NAME, inherited.next().getDocumentName());
        assertEquals(new DataTypeName("parent1"), inherited.next().getDocumentName());
        assertEquals(new DataTypeName("parent2"), inherited.next().getDocumentName());
        assertFalse(inherited.hasNext());

        inherited = parent1.getInheritedTypes().iterator();
        assertEquals(VespaDocumentType.NAME, inherited.next().getDocumentName());
        assertEquals(new DataTypeName("root"), inherited.next().getDocumentName());
        assertFalse(inherited.hasNext());

        inherited = parent2.getInheritedTypes().iterator();
        assertEquals(VespaDocumentType.NAME, inherited.next().getDocumentName());
        assertEquals(new DataTypeName("root"), inherited.next().getDocumentName());
        assertFalse(inherited.hasNext());

        inherited = root.getInheritedTypes().iterator();
        assertTrue(inherited.hasNext());
        assertEquals(inherited.next().getDocumentName(), VespaDocumentType.NAME);
        assertFalse(inherited.hasNext());


        Iterator fields = child.fieldSet().iterator();
        SDField field;

        field = (SDField) fields.next();
        assertEquals("rootfield", field.getName());

        field = (SDField) fields.next();
        assertEquals("overridden", field.getName());

        field = (SDField) fields.next();
        assertEquals("parent1field", field.getName());

        field = (SDField) fields.next();
        assertEquals("parent2field", field.getName());

        field = (SDField) fields.next();
        assertEquals("childfield", field.getName());

        // TODO: Test uninheriting
    }

}
