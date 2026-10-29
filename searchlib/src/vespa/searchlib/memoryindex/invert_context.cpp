// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "invert_context.h"

#include "document_inverter_context.h"

#include <vespa/document/base/exceptions.h>
#include <vespa/document/base/fieldpath.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/searchcommon/common/schema.h>
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
using search::index::Schema;

namespace {

/*
 * Returns the field path of an index of a struct field in an array of struct or map
 * (e.g. "array_of_struct.f", "map.key", "map.value" or "map.value.f"), or nullptr if the name
 * does not resolve to such a field path in the document type.
 */
std::unique_ptr<const FieldPath> get_struct_field_path(const DocumentType& doc_type, const std::string& name) {
    if (name.find('.') == std::string::npos) {
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
    const auto& complex_type = (*field_path)[0].getDataType();
    if (!(complex_type.isArray() || complex_type.isMap()) || !DocumentFieldExtractor::isSupported(*field_path)) {
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

/*
 * A struct field index gets one element per element in the complex field, so the schema
 * must have it as an array of string, and the struct field must be a string.
 */
bool is_valid_struct_field_index(const Schema::IndexField& index_field, const FieldPath& field_path) {
    return index_field.getDataType() == Schema::DataType::STRING &&
           index_field.getCollectionType() == Schema::CollectionType::ARRAY &&
           field_path.back().getDataType() == *document::DataType::STRING;
}

InvertContext::IndexedField make_indexed_field(const DocumentType& doc_type, const Schema::IndexField& index_field) {
    auto& name = index_field.getName();
    auto  struct_field_path = get_struct_field_path(doc_type, name);
    if (!struct_field_path) {
        return {get_field(doc_type, name), {}};
    }
    if (!is_valid_struct_field_index(index_field, *struct_field_path)) {
        LOG(error,
            "Mismatch between documentdefinition and schema. "
            "Index '%s' (%s %s) from schema is a struct field of type %s in document type '%s', "
            "expected an array of string index of a string struct field",
            name.c_str(), getTypeName(index_field.getCollectionType()).c_str(),
            getTypeName(index_field.getDataType()).c_str(), struct_field_path->back().getDataType().getName().c_str(),
            doc_type.getName().c_str());
        return {};
    }
    return {{}, std::move(struct_field_path)};
}

} // namespace

InvertContext::InvertContext(vespalib::ISequencedTaskExecutor::ExecutorId id)
    : BundledFieldsContext(id), _pushers(), _document_fields(), _document_uri_fields(), _data_type(nullptr) {
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
    auto& schema = doc_inv_context.get_schema();
    for (auto field_id : get_fields()) {
        _document_fields.emplace_back(make_indexed_field(doc_type, schema.getIndexField(field_id)));
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
