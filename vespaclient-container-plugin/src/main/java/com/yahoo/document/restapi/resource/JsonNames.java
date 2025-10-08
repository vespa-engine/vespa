// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.restapi.resource;

import com.fasterxml.jackson.core.io.SerializedString;

/**
 * Pre-serialized JSON field name strings to avoid implicit conversion from
 * <code>String</code> on every field key write.
 *
 * @author vekterli
 */
class JsonNames {
    private JsonNames() {}

    static final SerializedString CONTINUATION     = new SerializedString("continuation");
    static final SerializedString DOCUMENTS        = new SerializedString("documents");
    static final SerializedString DOCUMENT_COUNT   = new SerializedString("documentCount");
    static final SerializedString ID               = new SerializedString("id");
    static final SerializedString MESSAGE          = new SerializedString("message");
    static final SerializedString PATH_ID          = new SerializedString("pathId");
    static final SerializedString PERCENT_FINISHED = new SerializedString("percentFinished");
    static final SerializedString PUT              = new SerializedString("put");
    static final SerializedString REMOVE           = new SerializedString("remove");
    static final SerializedString SESSION_STATS    = new SerializedString("sessionStats");
    static final SerializedString SESSION_TRACE    = new SerializedString("sessionTrace");
    static final SerializedString SEVERITY         = new SerializedString("severity");
    static final SerializedString TEXT             = new SerializedString("text");
    static final SerializedString TOKEN            = new SerializedString("token");
}
