// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.Raw;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;

public class DocumentTestCaseBase {

    protected Field byteField = null;
    protected Field intField = null;
    protected Field rawField = null;
    protected Field floatField = null;
    protected Field stringField = null;
    protected Field minField = null;
    protected DocumentType testDocType = null;
    protected DocumentTypeManager docMan;

    public DocumentTestCaseBase() {
        docMan = new DocumentTypeManager();
        testDocType = new DocumentType("testdoc");

        testDocType.addField("byteattr", DataType.BYTE);
        testDocType.addField("intattr", DataType.INT);
        testDocType.addField("rawattr", DataType.RAW);
        testDocType.addField("floatattr", DataType.FLOAT);
        testDocType.addField("stringattr", DataType.STRING);
        testDocType.addField("Minattr", DataType.INT);
        testDocType.addField("Minattr2", DataType.INT);
        testDocType.addField("primitive1", DataType.INT);

        StructDataType sdt = new StructDataType("struct1");
        sdt.addField(new Field("primitive1", DataType.INT));
        sdt.addField(new Field("primitive2", DataType.INT));

        StructDataType sdt2 = new StructDataType("struct2");
        sdt2.addField(new Field("primitive1", DataType.INT));
        sdt2.addField(new Field("primitive2", DataType.INT));
        sdt2.addField(new Field("iarray", new ArrayDataType(DataType.INT)));
        sdt2.addField(new Field("sarray", new ArrayDataType(sdt)));
        sdt2.addField(new Field("smap", new MapDataType(DataType.STRING, DataType.STRING)));

        testDocType.addField(new Field("struct2", sdt2));

        StructDataType sdt3 = new StructDataType("struct3");
        sdt3.addField(new Field("primitive1", DataType.INT));
        sdt3.addField(new Field("ss", sdt2));
        sdt3.addField(new Field("structmap", new MapDataType(DataType.STRING, sdt2)));
        sdt3.addField(new Field("wset", new WeightedSetDataType(DataType.STRING, false, false)));
        sdt3.addField(new Field("structwset", new WeightedSetDataType(sdt2, false, false)));

        testDocType.addField(new Field("l1s1", sdt3));

        byteField = testDocType.getField("byteattr");
        intField = testDocType.getField("intattr");
        rawField = testDocType.getField("rawattr");
        floatField = testDocType.getField("floatattr");
        stringField = testDocType.getField("stringattr");
        minField = testDocType.getField("Minattr");

        testDocType.addFieldSets(Map.of("[document]", List.of("stringattr", "intattr")));

        docMan.registerDocumentType(testDocType);
    }

    public Document getTestDocument() {
        Document doc = new Document(docMan.getDocumentType("testdoc"), new DocumentId("id:ns:testdoc::http://www.ntnu.no/"));
        doc.setFieldValue(byteField.getName(), (byte) 30);
        doc.setFieldValue(byteField.getName(), (byte)30);
        doc.setFieldValue(intField.getName(), 50);

        ByteBuffer buf = ByteBuffer.allocate(7).put("hei der".getBytes());
        buf.flip();

        doc.setFieldValue(rawField, new Raw(buf));
        doc.setFieldValue(floatField, new FloatFieldValue((float) 3.56));
        doc.setFieldValue("stringattr", "tjohei");

        assertNotNull(doc.getId());
        assertNotNull(doc.getDataType());

        return doc;
    }
}
