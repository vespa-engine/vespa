// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "forcelink.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/updates.h>


namespace document {

ForceLink::ForceLink(void)
{
    if (time(NULL) == 0) {
        DocumentType          type("foo", 1);
        DocumentTypeRepo      repo(type);
        Document              document(repo, *repo.getDocumentType("foo"), DocumentId("doc:ns:bar"));
        DocumentUpdate        documentUpdate;
        MapValueUpdate        mapValueUpdate(std::make_unique<IntFieldValue>(3), std::make_unique<ClearValueUpdate>());
        AddValueUpdate        addValueUpdate(std::make_unique<IntFieldValue>(3));
        RemoveValueUpdate     removeValueUpdate(std::make_unique<IntFieldValue>(3));
        AssignValueUpdate     assignValueUpdate(std::make_unique<IntFieldValue>(3));
        ClearValueUpdate      clearValueUpdate;
        ArithmeticValueUpdate arithmeticValueUpdate(ArithmeticValueUpdate::Add, 3);
    }
}

}

