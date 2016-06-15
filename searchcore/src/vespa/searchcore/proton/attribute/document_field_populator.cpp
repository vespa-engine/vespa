// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.attribute.document_field_populator");
#include "document_field_populator.h"
#include "document_field_retriever.h"
#include <vespa/searchcore/proton/common/eventlogger.h>

using document::Document;
using search::index::Schema;
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

DocumentFieldPopulator::DocumentFieldPopulator(const Schema::AttributeField &field,
                                               search::AttributeVector::SP attr,
                                               const vespalib::string &subDbName)
    : _field(field),
      _attr(attr),
      _subDbName(subDbName),
      _documentsPopulated(0)
{
    if (LOG_WOULD_LOG(event)) {
        EventLogger::populateDocumentFieldStart(getFieldName(subDbName, field.getName()));
    }
}

DocumentFieldPopulator::~DocumentFieldPopulator()
{
    if (LOG_WOULD_LOG(event)) {
        EventLogger::populateDocumentFieldComplete(getFieldName(_subDbName, _field.getName()),
                _documentsPopulated);
    }
}

void
DocumentFieldPopulator::handleExisting(uint32_t lid, Document &doc)
{
    DocumentFieldRetriever::populate(lid, doc, _field, *_attr, false);
    ++_documentsPopulated;
}

} // namespace proton
