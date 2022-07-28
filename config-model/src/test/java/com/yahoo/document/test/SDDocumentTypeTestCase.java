// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.test;

import com.yahoo.document.DataType;
import com.yahoo.document.DataTypeName;
import com.yahoo.documentmodel.VespaDocumentType;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Thomas Gundersen
 * @author bratseth
 */
public class SDDocumentTypeTestCase extends AbstractSchemaTestCase {

    // Verify that we can register and retrieve fields.
    @Test
    void testSetGet() {
        SDDocumentType docType = new SDDocumentType("testdoc");
        docType.addField("Bongle", DataType.STRING);
        docType.addField("nalle", DataType.INT);

        assertNotNull(docType.getField("Bongle").getName());
        assertNull(docType.getField("bongle"));

    }

    @Test
    void testInheritance() {
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
    }

    @Test
    void testStructInheritance() throws ParseException {
        String schemaLines = joinLines(
                "schema test {" +
                        "  document test {" +
                        "    struct parent_struct {" +
                        "      field parent_struct_field_1 type string {}" +
                        "    }" +
                        "    struct child_struct inherits parent_struct {" +
                        "      field child_struct_field_1 type string {}" +
                        "    }" +
                        "    field child_array type array<child_struct> {" +
                        "      indexing: summary\n" +
                        "      struct-field child_struct_field_1 { indexing: attribute }" +
                        "      struct-field parent_struct_field_1  { indexing: attribute }" +
                        "    }" +
                        "  }" +
                        "}");

        ApplicationBuilder builder = new ApplicationBuilder(new DeployLoggerStub());
        builder.addSchema(schemaLines);
        builder.build(true);
        var application = builder.application();

        SDDocumentType type = application.schemas().get("test").getDocument();

        SDDocumentType parent_struct = type.getOwnedType("parent_struct");
        assertEquals(1, parent_struct.fieldSet().size());
        assertNotNull(parent_struct.getField("parent_struct_field_1"));

        SDDocumentType child_struct = type.getOwnedType("child_struct");
        assertTrue(child_struct.inheritedTypes().containsKey(parent_struct.getDocumentName()));
        assertEquals(2, child_struct.fieldSet().size());
        assertNotNull(child_struct.getField("child_struct_field_1"));
        assertNotNull(child_struct.getField("parent_struct_field_1"));
    }

}
