// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docprocs.indexing;

import com.yahoo.document.ArrayDataType;
import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.MapDataType;
import com.yahoo.document.StructDataType;
import com.yahoo.document.annotation.SpanTree;
import com.yahoo.document.annotation.SpanTrees;
import com.yahoo.document.datatypes.Array;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.datatypes.MapFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.datatypes.Struct;
import com.yahoo.document.datatypes.WeightedSet;
import com.yahoo.document.fieldpathupdate.AssignFieldPathUpdate;
import com.yahoo.document.fieldpathupdate.FieldPathUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.MapValueUpdate;
import com.yahoo.document.update.ValueUpdate;
import com.yahoo.vespa.indexinglanguage.AdapterFactory;
import com.yahoo.vespa.indexinglanguage.SimpleAdapterFactory;
import com.yahoo.vespa.indexinglanguage.expressions.Expression;
import com.yahoo.vespa.indexinglanguage.expressions.IndexExpression;
import com.yahoo.vespa.indexinglanguage.expressions.InputExpression;
import com.yahoo.vespa.indexinglanguage.expressions.StatementExpression;
import com.yahoo.vespa.indexinglanguage.parser.ParseException;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
@SuppressWarnings("unchecked")
public class DocumentScriptTestCase {

    private static final AdapterFactory ADAPTER_FACTORY = new SimpleAdapterFactory();

    @Test
    public void requireThatDocumentWithExtraFieldsThrow() throws ParseException {
        assertFail("Field 'extraField' is not part of the declared document type 'documentType'.",
                   newDocument(new StringFieldValue("foo"), new StringFieldValue("bar")));
        assertFail("Field 'extraField' is not part of the declared document type 'documentType'.",
                   newDocument(null, new StringFieldValue("bar")));
    }

    @Test
    public void requireThatFieldUpdateToExtraFieldsThrow() throws ParseException {
        assertFail("Field 'extraField' is not part of the declared document type 'documentType'.",
                   newFieldUpdate(new StringFieldValue("foo"), new StringFieldValue("bar")));
        assertFail("Field 'extraField' is not part of the declared document type 'documentType'.",
                   newFieldUpdate(null, new StringFieldValue("bar")));
    }

    @Test
    public void requireThatPathUpdateToExtraFieldsThrow() throws ParseException {
        assertFail("Field 'extraField' is not part of the declared document type 'documentType'.",
                   newPathUpdate(new StringFieldValue("foo"), new StringFieldValue("bar")));
        assertFail("Field 'extraField' is not part of the declared document type 'documentType'.",
                   newPathUpdate(null, new StringFieldValue("bar")));
    }

    @Test
    public void requireThatLinguisticsSpanTreeIsRemovedFromStringFields() {
        StringFieldValue in = newString(SpanTrees.LINGUISTICS, "mySpanTree");
        StringFieldValue out = (StringFieldValue)processDocument(in);
        assertSpanTrees(out, "mySpanTree");

        out = (StringFieldValue)processFieldUpdate(in).getValue();
        assertSpanTrees(out, "mySpanTree");

        out = (StringFieldValue)processPathUpdate(in).getValue();
        assertSpanTrees(out, "mySpanTree");
    }

    @Test
    public void requireThatLinguisticsSpanTreeIsRemovedFromArrayStringFields() {
        Array<StringFieldValue> in = new Array<>(DataType.getArray(DataType.STRING));
        in.add(newString(SpanTrees.LINGUISTICS, "mySpanTree"));

        Array<StringFieldValue> out = (Array<StringFieldValue>)processDocument(in);
        assertEquals(1, out.size());
        assertSpanTrees(out.get(0), "mySpanTree");

        out = (Array<StringFieldValue>)processFieldUpdate(in).getValue();
        assertEquals(1, out.size());
        assertSpanTrees(out.get(0), "mySpanTree");

        out = (Array<StringFieldValue>)processPathUpdate(in).getValue();
        assertEquals(1, out.size());
        assertSpanTrees(out.get(0), "mySpanTree");
    }

    @Test
    public void requireThatLinguisticsSpanTreeIsRemovedFromWsetStringFields() {
        WeightedSet<StringFieldValue> in = new WeightedSet<>(DataType.getWeightedSet(DataType.STRING));
        in.put(newString(SpanTrees.LINGUISTICS, "mySpanTree"), 69);

        WeightedSet<StringFieldValue> out = (WeightedSet<StringFieldValue>)processDocument(in);
        assertEquals(1, out.size());
        assertSpanTrees(out.keySet().iterator().next(), "mySpanTree");

        out = (WeightedSet<StringFieldValue>)processFieldUpdate(in).getValue();
        assertEquals(1, out.size());
        assertSpanTrees(out.keySet().iterator().next(), "mySpanTree");

        out = (WeightedSet<StringFieldValue>)processPathUpdate(in).getValue();
        assertEquals(1, out.size());
        assertSpanTrees(out.keySet().iterator().next(), "mySpanTree");
    }

    @Test
    public void requireThatLinguisticsSpanTreeIsRemovedFromMapStringStringFields() {
        MapFieldValue<StringFieldValue, StringFieldValue> in =
                new MapFieldValue<>(DataType.getMap(DataType.STRING, DataType.STRING));
        in.put(newString(SpanTrees.LINGUISTICS, "myKeySpanTree"),
               newString(SpanTrees.LINGUISTICS, "myValueSpanTree"));

        MapFieldValue<StringFieldValue, StringFieldValue> out;
        out = (MapFieldValue<StringFieldValue, StringFieldValue>)processDocument(in);
        assertEquals(1, out.size());
        assertSpanTrees(out.keySet().iterator().next(), "myKeySpanTree");
        assertSpanTrees(out.values().iterator().next(), "myValueSpanTree");

        out = (MapFieldValue<StringFieldValue, StringFieldValue>)processFieldUpdate(in).getValue();
        assertEquals(1, out.size());
        assertSpanTrees(out.keySet().iterator().next(), "myKeySpanTree");
        assertSpanTrees(out.values().iterator().next(), "myValueSpanTree");

        out = (MapFieldValue<StringFieldValue, StringFieldValue>)processPathUpdate(in).getValue();
        assertEquals(1, out.size());
        assertSpanTrees(out.keySet().iterator().next(), "myKeySpanTree");
        assertSpanTrees(out.values().iterator().next(), "myValueSpanTree");
    }

    @Test
    public void requireThatLinguisticsSpanTreeIsRemovedFromStructStringFields() {
        StructDataType structType = new StructDataType("myStruct");
        structType.addField(new Field("myString", DataType.STRING));
        Struct in = new Struct(structType);
        in.setFieldValue("myString", newString(SpanTrees.LINGUISTICS, "mySpanTree"));

        Struct out = (Struct)processDocument(in);
        assertSpanTrees(out.getFieldValue("myString"), "mySpanTree");

        StringFieldValue str = (StringFieldValue)((MapValueUpdate)processFieldUpdate(in)).getUpdate().getValue();
        assertSpanTrees(str, "mySpanTree");

        str = (StringFieldValue)((MapValueUpdate)processFieldUpdate(in)).getUpdate().getValue();
        assertSpanTrees(str, "mySpanTree");
    }

    private class FieldPathFixture {
        final DocumentType type;
        final StructDataType structType;
        final DataType structMap;
        final DataType structArray;

        FieldPathFixture() {
            type = newDocumentType();
            structType = new StructDataType("mystruct");
            structType.addField(new Field("title", DataType.STRING));
            structType.addField(new Field("rating", DataType.INT));
            structArray = new ArrayDataType(structType);
            type.addField(new Field("structarray", structArray));
            structMap = new MapDataType(DataType.STRING, structType);
            type.addField(new Field("structmap", structMap));
            type.addField(new Field("structfield", structType));
        }

        DocumentUpdate executeWithUpdate(String fieldName, FieldPathUpdate updateIn) {
            DocumentUpdate update = new DocumentUpdate(type, "doc:scheme:");
            update.addFieldPathUpdate(updateIn);
            return newScript(type, fieldName).execute(ADAPTER_FACTORY, update);
        }

        FieldPathUpdate executeWithUpdateAndExpectFieldPath(String fieldName, FieldPathUpdate updateIn) {
            DocumentUpdate update = executeWithUpdate(fieldName, updateIn);
            assertEquals(1, update.getFieldPathUpdates().size());
            return update.getFieldPathUpdates().get(0);
        }
    }

    @Test
    public void array_field_path_updates_survive_indexing_scripts() {
        FieldPathFixture f = new FieldPathFixture();

        Struct newElemValue = new Struct(f.structType);
        newElemValue.setFieldValue("title", "iron moose 2, the moosening");

        FieldPathUpdate updated = f.executeWithUpdateAndExpectFieldPath("structarray", new AssignFieldPathUpdate(f.type, "structarray[10]", newElemValue));

        assertTrue(updated instanceof AssignFieldPathUpdate);
        AssignFieldPathUpdate assignUpdate = (AssignFieldPathUpdate)updated;
        assertEquals("structarray[10]", assignUpdate.getOriginalFieldPath());
        assertEquals(newElemValue, assignUpdate.getFieldValue());
    }

    @Test
    public void map_field_path_updates_survive_indexing_scripts() {
        FieldPathFixture f = new FieldPathFixture();

        Struct newElemValue = new Struct(f.structType);
        newElemValue.setFieldValue("title", "iron moose 3, moose in new york");

        FieldPathUpdate updated = f.executeWithUpdateAndExpectFieldPath("structmap", new AssignFieldPathUpdate(f.type, "structmap{foo}", newElemValue));

        assertTrue(updated instanceof AssignFieldPathUpdate);
        AssignFieldPathUpdate assignUpdate = (AssignFieldPathUpdate)updated;
        assertEquals("structmap{foo}", assignUpdate.getOriginalFieldPath());
        assertEquals(newElemValue, assignUpdate.getFieldValue());
    }

    @Test
    public void nested_struct_fieldpath_update_is_not_converted_to_regular_field_value_update() {
        FieldPathFixture f = new FieldPathFixture();

        StringFieldValue newTitleValue = new StringFieldValue("iron moose 4, moose with a vengeance");
        DocumentUpdate update = f.executeWithUpdate("structfield", new AssignFieldPathUpdate(f.type, "structfield.title", newTitleValue));

        Document doc = new Document(f.type, "id:test:documentType::balle");
        Struct s = new Struct(f.structType);
        s.setFieldValue("title", new StringFieldValue("banan"));
        doc.setFieldValue("structfield", s);

        update.applyTo(doc);

        assertEquals(1, update.getFieldPathUpdates().size());
        assertEquals(0, update.getFieldUpdates().size());
        assertTrue(update.getFieldPathUpdates().get(0) instanceof AssignFieldPathUpdate);
        AssignFieldPathUpdate assignUpdate = (AssignFieldPathUpdate)update.getFieldPathUpdates().get(0);
        assertEquals("structfield.title", assignUpdate.getOriginalFieldPath());
        assertEquals(newTitleValue, assignUpdate.getFieldValue());
    }

    private static FieldValue processDocument(FieldValue fieldValue) {
        DocumentType docType = new DocumentType("myDocumentType");
        docType.addField("myField", fieldValue.getDataType());
        Document doc = new Document(docType, "doc:scheme:");
        doc.setFieldValue("myField", fieldValue.clone());
        doc = newScript(docType).execute(ADAPTER_FACTORY, doc);
        return doc.getFieldValue("myField");
    }

    private static ValueUpdate<?> processFieldUpdate(FieldValue fieldValue) {
        DocumentType docType = new DocumentType("myDocumentType");
        docType.addField("myField", fieldValue.getDataType());
        DocumentUpdate update = new DocumentUpdate(docType, "doc:scheme:");
        update.addFieldUpdate(FieldUpdate.createAssign(docType.getField("myField"), fieldValue));
        update = newScript(docType).execute(ADAPTER_FACTORY, update);
        return update.getFieldUpdate("myField").getValueUpdate(0);
    }

    private static ValueUpdate<?> processPathUpdate(FieldValue fieldValue) {
        DocumentType docType = new DocumentType("myDocumentType");
        docType.addField("myField", fieldValue.getDataType());
        DocumentUpdate update = new DocumentUpdate(docType, "doc:scheme:");
        update.addFieldPathUpdate(new AssignFieldPathUpdate(docType, "myField", fieldValue));
        update = newScript(docType).execute(ADAPTER_FACTORY, update);
        return update.getFieldUpdate("myField").getValueUpdate(0);
    }

    private static DocumentScript newScript(DocumentType docType, String fieldName) {
        return new DocumentScript(docType.getName(), Collections.singletonList(fieldName),
                new StatementExpression(new InputExpression(fieldName),
                        new IndexExpression(fieldName)));
    }

    private static DocumentScript newScript(DocumentType docType) {
        String fieldName = docType.getFields().iterator().next().getName();
        return newScript(docType, fieldName);
    }

    private static StringFieldValue newString(String... spanTrees) {
        StringFieldValue ret = new StringFieldValue("foo");
        for (String spanTree : spanTrees) {
            ret.setSpanTree(new SpanTree(spanTree));
        }
        return ret;
    }

    private static void assertSpanTrees(FieldValue actual, String... expectedSpanTrees) {
        assertTrue(actual instanceof StringFieldValue);
        StringFieldValue str = (StringFieldValue)actual;
        assertEquals(new ArrayList<>(Arrays.asList(expectedSpanTrees)),
                     new ArrayList<>(str.getSpanTreeMap().keySet()));
    }

    private static DocumentType newDocumentType() {
        DocumentType type = new DocumentType("documentType");
        type.addField("documentField", DataType.STRING);
        type.addField("extraField", DataType.STRING);

        return type;
    }

    private static Document newDocument(FieldValue documentFieldValue, FieldValue extraFieldValue) {
        Document document = new Document(newDocumentType(), "doc:scheme:");
        if (documentFieldValue != null) {
            document.setFieldValue("documentField", documentFieldValue);
        }
        if (extraFieldValue != null) {
            document.setFieldValue("extraField", extraFieldValue);
        }
        return document;
    }

    private static DocumentUpdate newFieldUpdate(FieldValue documentFieldValue, FieldValue extraFieldValue) {
        DocumentType type = newDocumentType();
        DocumentUpdate update = new DocumentUpdate(type, "doc:scheme:");
        if (documentFieldValue != null) {
            update.addFieldUpdate(FieldUpdate.createAssign(type.getField("documentField"), documentFieldValue));
        }
        if (extraFieldValue != null) {
            update.addFieldUpdate(FieldUpdate.createAssign(type.getField("extraField"), extraFieldValue));
        }
        return update;
    }

    private static DocumentUpdate newPathUpdate(FieldValue documentFieldValue, FieldValue extraFieldValue) {
        DocumentType type = newDocumentType();
        DocumentUpdate update = new DocumentUpdate(type, "doc:scheme:");
        if (documentFieldValue != null) {
            update.addFieldPathUpdate(new AssignFieldPathUpdate(type, "documentField", documentFieldValue));
        }
        if (extraFieldValue != null) {
            update.addFieldPathUpdate(new AssignFieldPathUpdate(type, "extraField", extraFieldValue));
        }
        return update;
    }

    private static void assertFail(String expectedException, Document document) throws ParseException {
        try {
            execute(document);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(expectedException, e.getMessage());
        }
    }

    private static void assertFail(String expectedException, DocumentUpdate update) throws ParseException {
        try {
            execute(update);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals(expectedException, e.getMessage());
        }
    }

    private static Document execute(Document document) throws ParseException {
        return newScript().execute(new SimpleAdapterFactory(), document);
    }

    private static DocumentUpdate execute(DocumentUpdate update) throws ParseException {
        return newScript().execute(new SimpleAdapterFactory(), update);
    }

    private static DocumentScript newScript() throws ParseException {
        return new DocumentScript("documentType", Arrays.asList("documentField"),
                                  Expression.fromString("input documentField | index documentField"));
    }
}
