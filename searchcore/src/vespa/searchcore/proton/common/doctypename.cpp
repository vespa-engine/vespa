// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doctypename.h"
#include <vespa/searchlib/engine/request.h>
#include <vespa/document/datatype/documenttype.h>

namespace proton {


DocTypeName::DocTypeName(const search::engine::Request &request)
    : _name(request.propertiesMap.matchProperties().lookup("documentdb", "searchdoctype").get(""))
{}


DocTypeName::DocTypeName(const document::DocumentType &docType)
    : _name(docType.getName())
{}

}
