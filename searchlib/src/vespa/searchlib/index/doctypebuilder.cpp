// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doctypebuilder.h"
#include <vespa/document/datatype/urldatatype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/repo/configbuilder.h>

using namespace document;

namespace search::index {
namespace {

DataType::Type convert(Schema::DataType type) {
    switch (type) {
    case schema::DataType::BOOL:
    case schema::DataType::UINT2:
    case schema::DataType::UINT4:
    case schema::DataType::INT8:
        return DataType::T_BYTE;
    case schema::DataType::INT16:
        return DataType::T_SHORT;
    case schema::DataType::INT32:
        return DataType::T_INT;
    case schema::DataType::INT64:
        return DataType::T_LONG;
    case schema::DataType::FLOAT:
        return DataType::T_FLOAT;
    case schema::DataType::DOUBLE:
        return DataType::T_DOUBLE;
    case schema::DataType::STRING:
        return DataType::T_STRING;
    case schema::DataType::RAW:
        return DataType::T_RAW;
    case schema::DataType::BOOLEANTREE:
        return DataType::T_PREDICATE;
    case schema::DataType::TENSOR:
        return DataType::T_TENSOR;
    default:
        break;
    }
    assert(!"Unknown datatype in schema");
    return DataType::MAX;
}

void
insertStructType(document::DocumenttypesConfig::Documenttype & cfg, const StructDataType & structType)
{
    typedef document::DocumenttypesConfig DTC;
    DTC::Documenttype::Datatype::Sstruct cfgStruct;
    cfgStruct.name = structType.getName();
    Field::Set fieldSet = structType.getFieldSet();
    for (const Field * field : fieldSet) {
        DTC::Documenttype::Datatype::Sstruct::Field sField;
        sField.name = field->getName();
        sField.datatype = field->getDataType().getId();
        sField.id = field->getId();
        cfgStruct.field.push_back(sField);
    }
    cfg.datatype.push_back(DTC::Documenttype::Datatype());
    cfg.datatype.back().sstruct = cfgStruct;
    cfg.datatype.back().id = structType.getId();
}

using namespace document::config_builder;

TypeOrId makeCollection(TypeOrId datatype, Schema::CollectionType collection_type) {
    switch (collection_type) {
    case schema::CollectionType::ARRAY:
        return Array(datatype);
    case schema::CollectionType::WEIGHTEDSET:
        // TODO: consider using array of struct<primitive,int32> to keep order
        return Wset(datatype);
    default:
        return datatype;
    }
}

struct TypeCache {
    std::map<std::pair<int, Schema::CollectionType>, TypeOrId> types;

    TypeOrId getType(TypeOrId datatype, Schema::CollectionType c_type) {
        TypeOrId type = makeCollection(datatype, c_type);
        std::pair<int, Schema::CollectionType> key = std::make_pair(datatype.id, c_type);
        if (types.find(key) == types.end()) {
            types.insert(std::make_pair(key, type));
        }
        return types.find(key)->second;
    }
};

}

DocTypeBuilder::DocTypeBuilder(const Schema &schema)
    : _schema(schema),
      _iFields()
{
    _iFields.setup(schema);
}

document::DocumenttypesConfig DocTypeBuilder::makeConfig() const {
    using namespace document::config_builder;
    TypeCache type_cache;

    typedef std::set<vespalib::string> UsedFields;
    UsedFields usedFields;

    Struct header_struct("searchdocument.header");
    header_struct.setId(-1505212454);

    for (size_t i = 0; i < _iFields._textFields.size(); ++i) {
        const Schema::IndexField &field =
            _schema.getIndexField(_iFields._textFields[i]);

        // only handles string fields for now
        assert(field.getDataType() == schema::DataType::STRING);
        header_struct.addField(field.getName(), type_cache.getType(
                        DataType::T_STRING, field.getCollectionType()));
        usedFields.insert(field.getName());
    }

    const int32_t uri_type = document::UrlDataType::getInstance().getId();
    for (size_t i = 0; i < _iFields._uriFields.size(); ++i) {
        const Schema::IndexField &field =
            _schema.getIndexField(_iFields._uriFields[i]._all);

        // only handles string fields for now
        assert(field.getDataType() == schema::DataType::STRING);
        header_struct.addField(field.getName(), type_cache.getType(
                        uri_type, field.getCollectionType()));
        usedFields.insert(field.getName());
    }

    for (uint32_t i = 0; i < _schema.getNumAttributeFields(); ++i) {
        const Schema::AttributeField &field = _schema.getAttributeField(i);
        UsedFields::const_iterator usf = usedFields.find(field.getName());
        if (usf != usedFields.end()) {
            continue;   // taken as index field
        }
        auto type_id = convert(field.getDataType());
        if (type_id == DataType::T_TENSOR) {
            header_struct.addTensorField(field.getName(), field.get_tensor_spec());
        } else {
            header_struct.addField(field.getName(), type_cache.getType(
                    type_id, field.getCollectionType()));
        }
        usedFields.insert(field.getName());
    }

    for (uint32_t i = 0; i < _schema.getNumSummaryFields(); ++i) {
        const Schema::SummaryField &field = _schema.getSummaryField(i);
        UsedFields::const_iterator usf = usedFields.find(field.getName());
        if (usf != usedFields.end()) {
            continue;   // taken as index field or attribute field
        }
        auto type_id  = convert(field.getDataType());
        if (type_id == DataType::T_TENSOR) {
            header_struct.addTensorField(field.getName(), field.get_tensor_spec());
        } else {
            header_struct.addField(field.getName(), type_cache.getType(
                    type_id, field.getCollectionType()));
        }
        usedFields.insert(field.getName());
    }

    DocumenttypesConfigBuilderHelper builder;
    builder.document(-645763131, "searchdocument",
                     header_struct, Struct("searchdocument.body"));
    return builder.config();
}

document::DocumenttypesConfig
DocTypeBuilder::makeConfig(const DocumentType &docType)
{
    typedef document::DocumenttypesConfigBuilder DTC;
    DTC cfg;
    { // document type
        DTC::Documenttype dtype;
        dtype.id = docType.getId();
        dtype.name = docType.getName();
        // TODO(vekterli): remove header/body config
        dtype.headerstruct = docType.getFieldsType().getId();
        dtype.bodystruct = docType.getFieldsType().getId();
        cfg.documenttype.push_back(dtype);
    }
    insertStructType(cfg.documenttype[0], docType.getFieldsType());
    return cfg;
}

}
