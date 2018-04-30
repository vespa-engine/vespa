// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import com.yahoo.document.*;
import com.yahoo.document.datatypes.FloatFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.serialization.DocumentSerializer;
import com.yahoo.document.serialization.DocumentSerializerFactory;
import com.yahoo.document.update.FieldUpdate;
import org.junit.Test;

import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DocumentListTestCase {

    @Test
    @SuppressWarnings("deprecation")
    public void testSelfSerializationAndWriteJavaFile() throws Exception {
        DocumentTypeManager docMan = new DocumentTypeManager();
        DocumentTypeManagerConfigurer.configure(docMan, "file:src/test/files/documentmanager.cfg");

        DocumentType bmType = docMan.getDocumentType("benchmark");
        DocumentPut put1 = new DocumentPut(bmType, "userdoc:foo:99999999:1");
        put1.getDocument().setFieldValue("headerstring", "foo");
        DocumentRemove doc2 = new DocumentRemove(new DocumentId("userdoc:foo:99999999:2"));
        DocumentPut put3 = new DocumentPut(bmType, "userdoc:foo:99999999:3");
        put3.getDocument().setFieldValue("bodyfloat", new FloatFieldValue(5.5f));


        DocumentUpdate docUp = new DocumentUpdate(docMan.getDocumentType("benchmark"), new DocumentId("userdoc:foo:99999999:4"));
        docUp.addFieldUpdate(FieldUpdate.createAssign(docUp.getType().getField("bodystring"), new StringFieldValue("ballooooo")));

        List<Entry> entries = new ArrayList<>();
        entries.add(Entry.create(put1));
        entries.add(Entry.create(doc2));
        entries.add(Entry.create(put3));
        entries.add(Entry.create(docUp));

        DocumentList documentList = DocumentList.create(entries);

        DocumentSerializer gbuf = DocumentSerializerFactory.create42();
        gbuf.putInt(null, 1234); // Add some data to avoid special case where position() is 0 for buffer used.
        int startPos = gbuf.getBuf().position();
        documentList.serialize(gbuf);

        int size = gbuf.getBuf().position() - startPos;
        byte[] data = new byte[size];
        gbuf.getBuf().position(startPos);
        gbuf.getBuf().get(data);
        FileOutputStream stream = new FileOutputStream("./src/test/files/documentlist-java.dat");
        stream.write(data);
        stream.close();

        gbuf.getBuf().position(0);

        DocumentList documentList2 = DocumentList.create(docMan, data);

        assertEquals(4, documentList2.size());
        Entry entry1 = documentList2.get(0);
        assertEquals(0L, entry1.getTimestamp());
        assertFalse(entry1.isBodyStripped());
        assertFalse(entry1.isRemoveEntry());
        assertFalse(entry1.isUpdateEntry());
        assertTrue(entry1.getDocumentOperation() instanceof DocumentPut);
        assertEquals(new StringFieldValue("foo"), ((DocumentPut) entry1.getDocumentOperation()).getDocument().getFieldValue("headerstring"));

        Entry entry2 = documentList2.get(1);
        assertEquals(0L, entry2.getTimestamp());
        assertFalse(entry2.isBodyStripped());
        assertTrue(entry2.isRemoveEntry());
        assertFalse(entry2.isUpdateEntry());
        assertTrue(entry2.getDocumentOperation() instanceof DocumentRemove);

        Entry entry3 = documentList2.get(2);
        assertEquals(0L, entry3.getTimestamp());
        assertFalse(entry3.isBodyStripped());
        assertFalse(entry3.isRemoveEntry());
        assertFalse(entry3.isUpdateEntry());
        assertTrue(entry3.getDocumentOperation() instanceof DocumentPut);
        assertEquals(new FloatFieldValue(5.5f), ((DocumentPut) entry3.getDocumentOperation()).getDocument().getFieldValue("bodyfloat"));

        Entry entry4 = documentList2.get(3);
        assertEquals(0L, entry4.getTimestamp());
        assertFalse(entry4.isBodyStripped());
        assertFalse(entry4.isRemoveEntry());
        assertTrue(entry4.isUpdateEntry());
        assertTrue(entry4.getDocumentOperation() instanceof DocumentUpdate);
        assertEquals(new StringFieldValue("ballooooo"),((DocumentUpdate) entry4.getDocumentOperation()).getFieldUpdate(0).getValueUpdate(0).getValue());
    }

    @Test
    public void testContains() {
        DocumentTypeManager manager = new DocumentTypeManager();
        DocumentTypeManagerConfigurer.configure(manager, "file:src/test/files/documentmanager.cfg");

        DocumentType bmType = manager.getDocumentType("benchmark");
        DocumentPut put1 = new DocumentPut(bmType, "userdoc:foo:99999999:1");
        DocumentRemove remove1 = new DocumentRemove(new DocumentId("userdoc:foo:99999999:2"));
        DocumentPut put2 = new DocumentPut(bmType, "userdoc:foo:99999999:3");

        List<Entry> entries = new ArrayList<Entry>();
        entries.add(Entry.create(put1));
        entries.add(Entry.create(remove1));
        entries.add(Entry.create(put2));

        DocumentList documentList = DocumentList.create(entries);

        DocumentPut put3 = new DocumentPut(bmType, "userdoc:foo:99999999:1");
        DocumentRemove remove2 = new DocumentRemove(new DocumentId("userdoc:foo:99999999:2"));
        DocumentPut put4 = new DocumentPut(bmType, "userdoc:foo:99999999:3");

        List<Entry> entries2 = new ArrayList<Entry>();
        entries2.add(Entry.create(put3));
        entries2.add(Entry.create(remove2));
        entries2.add(Entry.create(put4));

        DocumentList documentList2 = DocumentList.create(entries2);

        assertTrue(documentList.containsAll(documentList2));

        Long t = put4.getDocument().getLastModified();
        put4.getDocument().setLastModified(13L);
        assertTrue(!documentList.containsAll(documentList2));
        put4.getDocument().setLastModified(t);

        assert(documentList.containsAll(documentList2));

        entries.remove(2);
        assertTrue(!documentList.containsAll(documentList2));
    }

}
