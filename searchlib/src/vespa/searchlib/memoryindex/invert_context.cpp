// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "invert_context.h"
#include "document_inverter_context.h"
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/index/schema_index_fields.h>

#include <vespa/log/log.h>
LOG_SETUP(".memoryindex.invert_context");

namespace search::memoryindex {

using document::Document;
using document::DocumentType;
using document::Field;

namespace {

std::unique_ptr<document::Field>
get_field(const DocumentType& doc_type, const vespalib::string& name)
{
    std::unique_ptr<Field> fp;
    if ( ! doc_type.hasField(name)) {
        LOG(error,
            "Mismatch between documentdefinition and schema. "
            "No field named '%s' from schema in document type '%s'",
            name.c_str(),
            doc_type.getName().c_str());
    } else {
        fp = std::make_unique<Field>(doc_type.getField(name));
    }
    return fp;
}

}


InvertContext::InvertContext(vespalib::ISequencedTaskExecutor::ExecutorId id)
    : BundledFieldsContext(id),
      _pushers(),
      _document_fields(),
      _document_uri_fields(),
      _data_type(nullptr)
{
}

InvertContext::~InvertContext() = default;

InvertContext::InvertContext(InvertContext&&) = default;

void
InvertContext::add_pusher(uint32_t pusher_id)
{
    _pushers.emplace_back(pusher_id);
}

void
InvertContext::set_data_type(const DocumentInverterContext &doc_inv_context, const Document& doc) const
{
    auto data_type(doc.getDataType());
    if (_data_type == data_type) {
        return;
    }
    auto& doc_type(doc.getType());
    _document_fields.clear();
    auto& schema = doc_inv_context.get_schema();
    for (auto field_id : get_fields()) {
        auto& name = schema.getIndexField(field_id).getName();
        _document_fields.emplace_back(get_field(doc_type, name));
    }
    _document_uri_fields.clear();
    auto& schema_index_fields = doc_inv_context.get_schema_index_fields();
    for (auto uri_field_id : get_uri_fields()) {
        auto& name = schema.getIndexField(schema_index_fields._uriFields[uri_field_id]._all).getName();
        _document_uri_fields.emplace_back(get_field(doc_type, name));
    }
    _data_type = data_type;
}

}
