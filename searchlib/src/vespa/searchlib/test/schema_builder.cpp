// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "schema_builder.h"
#include "doc_builder.h"
#include <vespa/document/datatype/collectiondatatype.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/searchcommon/common/schema.h>
#include <cassert>

using document::DataType;
using search::index::Schema;

namespace search::test {

namespace
{

search::index::schema::DataType
get_data_type(const DataType& type)
{
    using SrcType = DataType::Type;
    using DstType = search::index::schema::DataType;
    switch (type.getId()) {
    case SrcType::T_INT:
        return DstType::INT32;
    case SrcType::T_FLOAT:
        return DstType::FLOAT;
    case SrcType::T_STRING:
        return DstType::STRING;
    case SrcType::T_RAW:
        return DstType::RAW;
    case SrcType::T_LONG:
        return DstType::INT64;
    case SrcType::T_DOUBLE:
        return DstType::DOUBLE;
    case SrcType::T_BOOL:
        return DstType::BOOL;
    case SrcType::T_URI:
        return DstType::STRING;
    case SrcType::T_BYTE:
        return DstType::INT8;
    case SrcType::T_SHORT:
        return DstType::INT16;
    case SrcType::T_PREDICATE:
        return DstType::BOOLEANTREE;
    case SrcType::T_TENSOR:
        return DstType::TENSOR;
    default:
        abort();
    }
}

const DataType&
get_nested_type(const DataType& type)
{
    if (type.isArray() || type.isWeightedSet()) {
        return type.cast_collection()->getNestedType();
    }
    return type;
}

search::index::schema::CollectionType
get_collection_type(const document::DataType& type)
{
    using DstType = search::index::schema::CollectionType;
    if (type.isArray()) {
        return DstType::ARRAY;
    }
    if (type.isWeightedSet()) {
        return DstType::WEIGHTEDSET;
    }
    assert(!type.isMap());
    return DstType::SINGLE;
}

}


SchemaBuilder::SchemaBuilder(const DocBuilder& doc_builder)
    : _doc_builder(doc_builder),
      _schema(std::make_unique<Schema>())
{
}

SchemaBuilder::~SchemaBuilder() = default;

void
SchemaBuilder::add_index(vespalib::stringref field_name, std::optional<bool> interleaved_features)
{
    auto& field = _doc_builder.get_document_type().getField(field_name);
    auto ct = get_collection_type(field.getDataType());
    auto& type = get_nested_type(field.getDataType());
    auto dt = get_data_type(type);
    assert(dt == search::index::schema::DataType::STRING);
    Schema::IndexField index_field(field_name, dt, ct);
    if (interleaved_features.has_value()) {
        index_field.set_interleaved_features(interleaved_features.value());
    }
    if (type.getId() == DataType::Type::T_URI) {
        _schema->addUriIndexFields(index_field);
    } else {
        _schema->addIndexField(index_field);
    }
}

SchemaBuilder&
SchemaBuilder::add_indexes(std::vector<vespalib::stringref> field_names, std::optional<bool> interleaved_features)
{
    for (auto& field_name : field_names) {
        add_index(field_name, interleaved_features);
    }
    return *this;
}

SchemaBuilder&
SchemaBuilder::add_all_indexes(std::optional<bool> interleaved_features)
{
    auto fields = _doc_builder.get_document_type().getFieldSet();
    for (auto field : fields) {
        auto& type = get_nested_type(field->getDataType());
        auto dt = get_data_type(type);
        if (dt == search::index::schema::DataType::STRING) {
            add_index(field->getName(), interleaved_features);
        }
    }
    return *this;
}

void
SchemaBuilder::add_attribute(vespalib::stringref field_name)
{
    auto& field = _doc_builder.get_document_type().getField(field_name);
    auto ct = get_collection_type(field.getDataType());
    auto& type = get_nested_type(field.getDataType());
    auto dt = get_data_type(type);
    vespalib::string tensor_type_spec;
    assert(type.getId() != DataType::Type::T_URI);
    if (type.getId() == DataType::Type::T_TENSOR) {
        assert(ct == search::index::schema::CollectionType::SINGLE);
        tensor_type_spec = type.cast_tensor()->getTensorType().to_spec();
    }
    Schema::AttributeField attribute_field(field_name, dt, ct, tensor_type_spec);
    _schema->addAttributeField(attribute_field);
}

SchemaBuilder&
SchemaBuilder::add_attributes(std::vector<vespalib::stringref> field_names)
{
    for (auto& field_name : field_names) {
        add_attribute(field_name);
    }
    return *this;
}

SchemaBuilder&
SchemaBuilder::add_all_attributes()
{
    auto fields = _doc_builder.get_document_type().getFieldSet();
    for (auto field : fields) {
        auto& type = get_nested_type(field->getDataType());
        if (type.getId() != DataType::T_URI) {
            add_attribute(field->getName());
        }
    }
    return *this;
}

search::index::Schema
SchemaBuilder::build()
{
    return std::move(*_schema);
}

}
