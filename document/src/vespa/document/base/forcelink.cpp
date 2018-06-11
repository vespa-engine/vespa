// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "forcelink.h"
#include <vespa/document/update/updates.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/datatype/documenttype.h>


namespace document {

ForceLink::ForceLink(void)
{
    if (time(NULL) == 0) {
        DocumentType          type("foo", 1);
        Document              document(type, DocumentId("doc:ns:bar"));
        DocumentUpdate        documentUpdate;
        MapValueUpdate        mapValueUpdate(IntFieldValue(3), ClearValueUpdate());
        AddValueUpdate        addValueUpdate(IntFieldValue(3));
        RemoveValueUpdate     removeValueUpdate(IntFieldValue(3));
        AssignValueUpdate     assignValueUpdate(IntFieldValue(3));
        ClearValueUpdate      clearValueUpdate;
        ArithmeticValueUpdate arithmeticValueUpdate(ArithmeticValueUpdate::Add, 3);
    }
}

}

