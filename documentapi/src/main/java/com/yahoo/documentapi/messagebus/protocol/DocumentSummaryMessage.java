// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.vdslib.DocumentSummary;

public class DocumentSummaryMessage extends VisitorMessage {

    private DocumentSummary documentSummary = null;

    public void setDocumentSummary(DocumentSummary summary) {
        documentSummary = summary;
    }

    public DocumentSummary getResult() {
        return documentSummary;
    }

    @Override
    public DocumentReply createReply() {
        return new VisitorReply(DocumentProtocol.REPLY_DOCUMENTSUMMARY);
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_DOCUMENTSUMMARY;
    }
}
