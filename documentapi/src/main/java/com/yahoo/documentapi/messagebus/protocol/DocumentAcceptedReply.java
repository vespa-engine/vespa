// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

/**
 * Common base class for replies that indicate that a document was routed
 * to some recipient. Does not imply that the reply contains no errors!
 */
public abstract class DocumentAcceptedReply extends DocumentReply {
    protected DocumentAcceptedReply(int type) {
        super(type);
    }
}
