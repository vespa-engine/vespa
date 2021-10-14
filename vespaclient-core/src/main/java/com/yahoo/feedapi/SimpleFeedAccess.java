// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.document.Document;
import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.TestAndSetCondition;

public interface SimpleFeedAccess {

    void put(Document doc);
    void remove(DocumentId docId);
    void update(DocumentUpdate update);
    void put(Document doc, TestAndSetCondition condition);
    void remove(DocumentId docId, TestAndSetCondition condition);
    void update(DocumentUpdate update, TestAndSetCondition condition);
    boolean isAborted();
    void close();
}
