// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.docproc;

import com.yahoo.document.DataType;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentType;
import com.yahoo.document.DocumentTypeManager;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.Field;
import com.yahoo.document.datatypes.StringFieldValue;
import com.yahoo.document.update.AssignValueUpdate;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.ValueUpdate;
import org.junit.Test;

import java.util.List;
import java.util.StringTokenizer;

import static org.junit.Assert.assertEquals;

/**
 * Simple test case for testing that processing of both documents and
 * document updates works.
 *
 * @author Einar M R Rosenvinge
 */
public class ProcessingUpdateTestCase {

    private DocumentPut put;
    private DocumentUpdate update;

    private DocumentTypeManager dtm;

    @Test
    public void testProcessingUpdates() {
        DocumentType articleType = new DocumentType("article");
        articleType.addField(new Field("body", DataType.STRING, true));
        articleType.addField(new Field("title", DataType.STRING, true));
        dtm = new DocumentTypeManager();
        dtm.registerDocumentType(articleType);

        put = new DocumentPut(articleType, "doc:banana:apple");
        put.getDocument().setFieldValue("body", "this is the body of the article, blah blah blah");
        FieldUpdate upd = FieldUpdate.createAssign(articleType.getField("body"), new StringFieldValue("this is the updated body of the article, blahdi blahdi blahdi"));
        update = new DocumentUpdate(articleType, new DocumentId("doc:grape:orange"));
        update.addFieldUpdate(upd);

        DocprocService service = new DocprocService("update");
        DocumentProcessor firstP = new TitleDocumentProcessor();
        service.setCallStack(new CallStack().addLast(firstP));
        service.setInService(true);



        Processing p = new Processing();
        p.addDocumentOperation(put);
        p.addDocumentOperation(update);

        service.process(p);

        while (service.doWork()) {  }

        List<DocumentOperation> operations = p.getDocumentOperations();
        Document first = ((DocumentPut)operations.get(0)).getDocument();
        assertEquals(new StringFieldValue("this is the body of the article, blah blah blah"), first.getFieldValue("body"));
        assertEquals(new StringFieldValue("body blah blah blah "), first.getFieldValue("title"));

        DocumentUpdate second = (DocumentUpdate) operations.get(1);
        FieldUpdate firstUpd = second.getFieldUpdate(0);
        assertEquals(ValueUpdate.ValueUpdateClassID.ASSIGN, firstUpd.getValueUpdate(0).getValueUpdateClassID());
        assertEquals(new StringFieldValue("this is the updated body of the article, blahdi blahdi blahdi"), firstUpd.getValueUpdate(0)
                                                                                              .getValue());

        FieldUpdate secondUpd = second.getFieldUpdate(1);
        assertEquals(ValueUpdate.ValueUpdateClassID.ASSIGN, secondUpd.getValueUpdate(0).getValueUpdateClassID());
        assertEquals(new StringFieldValue("body blahdi blahdi blahdi "), secondUpd.getValueUpdate(0).getValue());
    }

    private class TitleDocumentProcessor extends SimpleDocumentProcessor {
        @Override
        public void process(DocumentPut doc) {
            put.getDocument().setFieldValue("title", extractTitle(put.getDocument().getFieldValue("body").toString()));
        }

        @Override
        public void process(DocumentUpdate upd) {
            FieldUpdate bodyFieldUpdate = upd.getFieldUpdate("body");
            AssignValueUpdate au = (AssignValueUpdate) bodyFieldUpdate.getValueUpdate(0);
            FieldUpdate titleUpd = FieldUpdate.createAssign(upd.getType().getField("title"), new StringFieldValue(extractTitle(((StringFieldValue) au.getValue()).getString())));
            upd.addFieldUpdate(titleUpd);
        }

        private String extractTitle(String body) {
            if (body == null) return null;
            StringTokenizer strTok = new StringTokenizer(body, " ");
            String title = "";
            while (strTok.hasMoreTokens()) {
                String word = strTok.nextToken();
                if (word.startsWith("b")) title += word + " ";
            }
            return title;
        }
    }

}
