// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

public class DocumentIgnoredReply extends DocumentReply {
    public DocumentIgnoredReply() {
        super(DocumentProtocol.REPLY_DOCUMENTIGNORED);
    }
}
