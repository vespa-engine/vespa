// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document.serialization;

import com.yahoo.document.*;
import com.yahoo.document.fieldpathupdate.*;
import com.yahoo.document.update.FieldUpdate;

/**
 * This interface is used to implement custom deserialization of document updates.
 *
 * @author <a href="mailto:thomasg@yahoo-inc.com">Thomas Gundersen</a>
 */
public interface DocumentUpdateReader {

    void read(DocumentUpdate update);

    void read(FieldUpdate update);

    void read(FieldPathUpdate update);

    void read(AssignFieldPathUpdate update);

    void read(AddFieldPathUpdate update);

    void read(RemoveFieldPathUpdate update);

    DocumentId readDocumentId();
    DocumentType readDocumentType();

}
