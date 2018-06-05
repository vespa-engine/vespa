// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "document_field_populator.h"
#include "document_field_retriever.h"
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchlib/attribute/attributevector.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.document_field_populator");

using document::Document;
using search::AttributeGuard;

namespace proton {

namespace {

vespalib::string
getFieldName(const vespalib::string &subDbName,
             const vespalib::string &fieldName)
{
    return subDbName + ".documentfield." + fieldName;
}

}

DocumentFieldPopulator::DocumentFieldPopulator(const vespalib::string &fieldName,
                                               AttributeVectorSP attr,
                                               const vespalib::string &subDbName)
    : _fieldName(fieldName),
      _attr(attr),
      _subDbName(subDbName),
      _documentsPopulated(0)
{
    if (LOG_WOULD_LOG(event)) {
        EventLogger::populateDocumentFieldStart(getFieldName(subDbName, fieldName));
    }
}

DocumentFieldPopulator::~DocumentFieldPopulator()
{
    if (LOG_WOULD_LOG(event)) {
        EventLogger::populateDocumentFieldComplete(getFieldName(_subDbName, _fieldName),
                _documentsPopulated);
    }
}

void
DocumentFieldPopulator::handleExisting(uint32_t lid, const std::shared_ptr<Document> &doc)
{
    DocumentFieldRetriever::populate(lid, *doc, _fieldName, *_attr, false);
    ++_documentsPopulated;
}

} // namespace proton
