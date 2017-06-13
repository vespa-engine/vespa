// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol.test;

import com.google.common.annotations.Beta;
import com.yahoo.component.Version;
import com.yahoo.document.*;
import com.yahoo.document.fieldpathupdate.RemoveFieldPathUpdate;
import com.yahoo.documentapi.messagebus.protocol.*;

import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Vegard Sjonfjell
 */

@Beta
public class Messages52TestCase extends Messages51TestCase {

    @Override
    protected Version version() {
        return new Version(5, 115, 0);
    }

    @Override
    protected boolean shouldTestCoverage() {
        return true;
    }

    @Override
    protected void registerTests(Map<Integer, RunnableTest> out) {
        super.registerTests(out);

        // This list MUST mirror the list of routable factories from the DocumentProtocol constructor that support
        // version 5.2. When adding tests to this list, please KEEP THEM ORDERED alphabetically like they are now.

        out.put(DocumentProtocol.MESSAGE_PUTDOCUMENT, new testPutDocumentMessage());
        out.put(DocumentProtocol.MESSAGE_UPDATEDOCUMENT, new testUpdateDocumentMessage());
        out.put(DocumentProtocol.MESSAGE_REMOVEDOCUMENT, new testRemoveDocumentMessage());
    }

    private static int BASE_MESSAGE_LENGTH = 5;
    private static String CONDITION_STRING = "There's just one condition";

    public class testPutDocumentMessage implements RunnableTest {
        @Override
        public void run() {
            PutDocumentMessage msg = new PutDocumentMessage(new DocumentPut(new Document(protocol.getDocumentTypeManager().getDocumentType("testdoc"), "doc:scheme:")));

            msg.setTimestamp(666);
            msg.setCondition(new TestAndSetCondition(CONDITION_STRING));

            assertEquals(BASE_MESSAGE_LENGTH + 41 + serializedLength(msg.getCondition().getSelection()), serialize("PutDocumentMessage", msg));

            for (Language lang : LANGUAGES) {
                final PutDocumentMessage deserializedMsg = (PutDocumentMessage)deserialize("PutDocumentMessage", DocumentProtocol.MESSAGE_PUTDOCUMENT, lang);
                assertEquals(msg.getDocumentPut().getDocument().getDataType().getName(), deserializedMsg.getDocumentPut().getDocument().getDataType().getName());
                assertEquals(msg.getDocumentPut().getDocument().getId().toString(), deserializedMsg.getDocumentPut().getDocument().getId().toString());
                assertEquals(msg.getTimestamp(), deserializedMsg.getTimestamp());
                assertEquals(msg.getCondition().getSelection(), deserializedMsg.getCondition().getSelection());
            }
        }
    }

    public class testUpdateDocumentMessage implements RunnableTest {
        @Override
        public void run() {
            DocumentType docType = protocol.getDocumentTypeManager().getDocumentType("testdoc");
            DocumentUpdate update = new DocumentUpdate(docType, new DocumentId("doc:scheme:"));
            update.addFieldPathUpdate(new RemoveFieldPathUpdate(docType, "intfield", "testdoc.intfield > 0"));

            final UpdateDocumentMessage msg = new UpdateDocumentMessage(update);
            msg.setNewTimestamp(777);
            msg.setOldTimestamp(666);
            msg.setCondition(new TestAndSetCondition(CONDITION_STRING));

            assertEquals(BASE_MESSAGE_LENGTH + 89 + serializedLength(msg.getCondition().getSelection()), serialize("UpdateDocumentMessage", msg));

            for (Language lang : LANGUAGES) {
                final UpdateDocumentMessage deserializedMsg = (UpdateDocumentMessage) deserialize("UpdateDocumentMessage", DocumentProtocol.MESSAGE_UPDATEDOCUMENT, lang);
                assertEquals(msg.getDocumentUpdate(), deserializedMsg.getDocumentUpdate());
                assertEquals(msg.getNewTimestamp(), deserializedMsg.getNewTimestamp());
                assertEquals(msg.getOldTimestamp(), deserializedMsg.getOldTimestamp());
                assertEquals(msg.getCondition().getSelection(), deserializedMsg.getCondition().getSelection());
            }
        }
    }

    public class testRemoveDocumentMessage implements RunnableTest {
        @Override
        public void run() {
            final RemoveDocumentMessage msg = new RemoveDocumentMessage(new DocumentId("doc:scheme:"));
            msg.setCondition(new TestAndSetCondition(CONDITION_STRING));

            assertEquals(BASE_MESSAGE_LENGTH + 16 + serializedLength(msg.getCondition().getSelection()), serialize("RemoveDocumentMessage", msg));

            for (Language lang : LANGUAGES) {
                final RemoveDocumentMessage deserializedMsg = (RemoveDocumentMessage)deserialize("RemoveDocumentMessage", DocumentProtocol.MESSAGE_REMOVEDOCUMENT, lang);
                assertEquals(deserializedMsg.getDocumentId().toString(), msg.getDocumentId().toString());
            }
        }
    }

    static int serializedLength(String str) {
        return 4 + str.length();
    }
}
