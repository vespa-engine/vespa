// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vsm/vsm/docsumconfig.h>
#include <vespa/searchsummary/docsummary/docsumfieldwriter.h>

using search::docsummary::IDocsumFieldWriter;
using search::docsummary::EmptyDFW;

namespace vsm {

IDocsumFieldWriter::UP
DynamicDocsumConfig::createFieldWriter(const string & fieldName, const string & overrideName, const string & argument, bool & rc)
{
    IDocsumFieldWriter::UP fieldWriter;
    if ((overrideName == "staticrank") ||
        (overrideName == "ranklog") ||
        (overrideName == "label") ||
        (overrideName == "project") ||
        (overrideName == "positions") ||
        (overrideName == "absdist") ||
        (overrideName == "subproject"))
    {
        fieldWriter = std::make_unique<EmptyDFW>();
        rc = true;
    } else if ((overrideName == "attribute") ||
            (overrideName == "attributecombiner") ||
            (overrideName == "geopos")) {
        rc = true;
    } else {
        fieldWriter = search::docsummary::DynamicDocsumConfig::createFieldWriter(fieldName, overrideName, argument, rc);
    }
    return fieldWriter;
}

}
