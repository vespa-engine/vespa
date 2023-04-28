// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.document.DocumentId;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentRemove;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.TestAndSetCondition;

public interface SimpleFeedAccess {

    void put(DocumentPut doc);
    void remove(DocumentRemove remove);
    void update(DocumentUpdate update);
    boolean isAborted();
    void close();
}
