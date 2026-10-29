// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "invert_context.h"

#include "document_inverter_context.h"

#include <vespa/document/base/exceptions.h>
#include <vespa/document/base/fieldpath.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchlib/common/document_field_extractor.h>
#include <vespa/searchlib/index/schema_index_fields.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".memoryindex.invert_context");

namespace search::memoryindex {

using document::Document;
using document::DocumentType;
using document::Field;
using document::FieldPath;

namespace {

/*
 * Returns the field path of an index of a struct field in an array of struct or map
 * (e.g. "array_of_struct.f", "map.key", "map.value" or "map.value.f"), or nullptr if the name
 * is not such a field path.
 */
std::unique_ptr<const FieldPath> get_struct_field_path(const DocumentType& doc_type, const std::string& name) {
    if (name.find('.') == std::string::npos || doc_type.hasField(name)) {
        return {};
    }
    auto field_path = std::make_unique<FieldPath>();
    try {
        doc_type.buildFieldPath(*field_path, name);
    } catch (document::FieldNotFoundException&) {
        return {};
    } catch (vespalib::IllegalArgumentException&) {
        return {};
    }
    if (field_path->size() < 2 || !DocumentFieldExtractor::isSupported(*field_path)) {
        return {};
    }
    return field_path;
}

std::unique_ptr<document::Field> get_field(const DocumentType& doc_type, const std::string& name) {
    std::unique_ptr<Field> fp;
    if (!doc_type.hasField(name)) {
        LOG(error,
            "Mismatch between documentdefinition and schema. "
            "No field named '%s' from schema in document type '%s'",
            name.c_str(), doc_type.getName().c_str());
    } else {
        fp = std::make_unique<Field>(doc_type.getField(name));
    }
    return fp;
}

} // namespace

InvertContext::InvertContext(vespalib::ISequencedTaskExecutor::ExecutorId id)
    : BundledFieldsContext(id),
      _pushers(),
      _document_fields(),
      _document_field_paths(),
      _document_uri_fields(),
      _data_type(nullptr) {
}

InvertContext::~InvertContext() = default;

InvertContext::InvertContext(InvertContext&&) = default;

void InvertContext::add_pusher(uint32_t pusher_id) {
    _pushers.emplace_back(pusher_id);
}

void InvertContext::set_data_type(const DocumentInverterContext& doc_inv_context, const Document& doc) const {
    auto data_type(doc.getDataType());
    if (_data_type == data_type) {
        return;
    }
    auto& doc_type(doc.getType());
    _document_fields.clear();
    _document_field_paths.clear();
    auto& schema = doc_inv_context.get_schema();
    for (auto field_id : get_fields()) {
        auto& name = schema.getIndexField(field_id).getName();
        auto  field_path = get_struct_field_path(doc_type, name);
        if (field_path) {
            _document_fields.emplace_back();
        } else {
            _document_fields.emplace_back(get_field(doc_type, name));
        }
        _document_field_paths.emplace_back(std::move(field_path));
    }
    _document_uri_fields.clear();
    auto& schema_index_fields = doc_inv_context.get_schema_index_fields();
    for (auto uri_field_id : get_uri_fields()) {
        auto& name = schema.getIndexField(schema_index_fields._uriFields[uri_field_id]._all).getName();
        _document_uri_fields.emplace_back(get_field(doc_type, name));
    }
    _data_type = data_type;
}

} // namespace search::memoryindex
