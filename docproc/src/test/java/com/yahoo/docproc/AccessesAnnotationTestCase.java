// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.datatypes.IntegerFieldValue;
import com.yahoo.document.datatypes.StringFieldValue;
import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class AccessesAnnotationTestCase {

    @Test
    public void requireThatFieldsAreRestricted() {
        DocumentType type = new DocumentType("album");
        type.addField("title", DataType.STRING);
        type.addField("artist", DataType.STRING);
        type.addField("year", DataType.INT);
        Document doc = new Document(type, new DocumentId("doc:map:test:1"));

        MyDocProc docProc = new MyDocProc();
        DocumentPut put = new DocumentPut(doc);
        Document proxy = new Call(docProc).configDoc(docProc, put).getDocument();
        proxy.setFieldValue("title", new StringFieldValue("foo"));
        try {
            proxy.setFieldValue("year", new IntegerFieldValue(69));
            fail("Should have failed");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            assertTrue(e.getMessage().matches(".*not allowed.*"));
        }

        proxy.getFieldValue("title");
        try {
            proxy.getFieldValue("year");
            fail("Should have failed");
        } catch (Exception e) {
            System.out.println(e.getMessage());
            assertTrue(e.getMessage().matches(".*not allowed.*"));
        }
    }

    @Accesses({
            @Accesses.Field(name = "title", dataType = "string", description = "What is done on field title",
                   annotations = @Accesses.Field.Tree(produces = { "sentences" })),
            @Accesses.Field(name = "artist", dataType = "string", description = "What is done on field artist",
                   annotations = {
                           @Accesses.Field.Tree(name = "root", produces = { "sentences" }),
                           @Accesses.Field.Tree(name = "root2", consumes = { "places" })
                   }),
            @Accesses.Field(name = "track", dataType = "string", description = "What is done on field track")
    })
    class MyDocProc extends DocumentProcessor {

        @Override
        public Progress process(Processing processing) {
            return null;
        }
    }
}
