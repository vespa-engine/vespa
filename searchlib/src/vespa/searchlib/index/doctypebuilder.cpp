// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "doctypebuilder.h"
#include <vespa/document/datatype/urldatatype.h>
#include <vespa/document/datatype/tensor_data_type.h>
#include <vespa/document/repo/configbuilder.h>

using namespace document;

namespace search::index {
namespace {

TensorDataType tensorDataType;

const DataType *convert(Schema::DataType type) {
    switch (type) {
    case schema::DataType::BOOL:
    case schema::DataType::UINT2:
    case schema::DataType::UINT4:
    case schema::DataType::INT8:
        return DataType::BYTE;
    case schema::DataType::INT16:
        return DataType::SHORT;
    case schema::DataType::INT32:
        return DataType::INT;
    case schema::DataType::INT64:
        return DataType::LONG;
    case schema::DataType::FLOAT:
        return DataType::FLOAT;
    case schema::DataType::DOUBLE:
        return DataType::DOUBLE;
    case schema::DataType::STRING:
        return DataType::STRING;
    case schema::DataType::RAW:
        return DataType::RAW;
    case schema::DataType::BOOLEANTREE:
        return DataType::PREDICATE;
    case schema::DataType::TENSOR:
        return &tensorDataType;
    default:
        break;
    }
    assert(!"Unknown datatype in schema");
    return 0;
}

void
insertStructType(document::DocumenttypesConfig::Documenttype & cfg,
                 const StructDataType & structType)
{
    typedef document::DocumenttypesConfig DTC;
    DTC::Documenttype::Datatype::Sstruct cfgStruct;
    cfgStruct.name = structType.getName();
    Field::Set fieldSet = structType.getFieldSet();
    for (Field::Set::const_iterator itr = fieldSet.begin();
         itr != fieldSet.end(); ++itr)
    {
        DTC::Documenttype::Datatype::Sstruct::Field field;
        field.name = (*itr)->getName();
        field.datatype = (*itr)->getDataType().getId();
        field.id = (*itr)->getId();
        cfgStruct.field.push_back(field);
    }
    cfg.datatype.push_back(DTC::Documenttype::Datatype());
    cfg.datatype.back().sstruct = cfgStruct;
    cfg.datatype.back().id = structType.getId();
}

}

DocTypeBuilder::UriField::UriField()
    : _all(Schema::UNKNOWN_FIELD_ID),
      _scheme(Schema::UNKNOWN_FIELD_ID),
      _host(Schema::UNKNOWN_FIELD_ID),
      _port(Schema::UNKNOWN_FIELD_ID),
      _path(Schema::UNKNOWN_FIELD_ID),
      _query(Schema::UNKNOWN_FIELD_ID),
      _fragment(Schema::UNKNOWN_FIELD_ID),
      _hostname(Schema::UNKNOWN_FIELD_ID)
{
}


bool
DocTypeBuilder::UriField::valid(const Schema &schema,
                                uint32_t fieldId,
                                const Schema::CollectionType &collectionType)
{
    if (fieldId == Schema::UNKNOWN_FIELD_ID)
        return false;
    const Schema::IndexField &field = schema.getIndexField(fieldId);
    if (field.getDataType() != schema::DataType::STRING)
        return false;
    if (field.getCollectionType() != collectionType)
        return false;
    return true;
}


bool
DocTypeBuilder::UriField::broken(const Schema &schema,
                                 const Schema::CollectionType &
                                 collectionType) const
{
    return !valid(schema, _all, collectionType) &&
        valid(schema, _scheme, collectionType) &&
        valid(schema, _host, collectionType) &&
        valid(schema, _port, collectionType) &&
        valid(schema, _path, collectionType) &&
        valid(schema, _query, collectionType) &&
        valid(schema, _fragment, collectionType);
}

bool
DocTypeBuilder::UriField::valid(const Schema &schema,
                                const Schema::CollectionType &
                                collectionType) const
{
    return valid(schema, _all, collectionType) &&
        valid(schema, _scheme, collectionType) &&
        valid(schema, _host, collectionType) &&
        valid(schema, _port, collectionType) &&
        valid(schema, _path, collectionType) &&
        valid(schema, _query, collectionType) &&
        valid(schema, _fragment, collectionType);
}


void
DocTypeBuilder::UriField::setup(const Schema &schema,
                                const vespalib::string &field)
{
    _all = schema.getIndexFieldId(field);
    _scheme = schema.getIndexFieldId(field + ".scheme");
    _host = schema.getIndexFieldId(field + ".host");
    _port = schema.getIndexFieldId(field + ".port");
    _path = schema.getIndexFieldId(field + ".path");
    _query = schema.getIndexFieldId(field + ".query");
    _fragment = schema.getIndexFieldId(field + ".fragment");
    _hostname = schema.getIndexFieldId(field + ".hostname");
}


void
DocTypeBuilder::UriField::markUsed(UsedFieldsMap &usedFields,
                                   uint32_t field)
{
    if (field == Schema::UNKNOWN_FIELD_ID)
        return;
    assert(usedFields.size() > field);
    usedFields[field] = true;
}


void
DocTypeBuilder::UriField::markUsed(UsedFieldsMap &usedFields) const
{
    markUsed(usedFields, _all);
    markUsed(usedFields, _scheme);
    markUsed(usedFields, _host);
    markUsed(usedFields, _port);
    markUsed(usedFields, _path);
    markUsed(usedFields, _query);
    markUsed(usedFields, _fragment);
    markUsed(usedFields, _hostname);
}



DocTypeBuilder::SchemaIndexFields::SchemaIndexFields()
    : _textFields(),
      _uriFields()
{
}

DocTypeBuilder::SchemaIndexFields::~SchemaIndexFields() {}

void
DocTypeBuilder::SchemaIndexFields::setup(const Schema &schema)
{
    uint32_t numIndexFields = schema.getNumIndexFields();
    UsedFieldsMap usedFields;
    usedFields.resize(numIndexFields);

    // Detect all URI fields (flattened structs).
    for (uint32_t fieldId = 0; fieldId < numIndexFields; ++fieldId) {
        const Schema::IndexField &field = schema.getIndexField(fieldId);
        const vespalib::string &name = field.getName();
        size_t dotPos = name.find('.');
        if (dotPos != vespalib::string::npos) {
            const vespalib::string suffix = name.substr(dotPos + 1);
            if (suffix == "scheme") {
                const vespalib::string shortName = name.substr(0, dotPos);
                UriField uriField;
                uriField.setup(schema, shortName);
                if (uriField.valid(schema, field.getCollectionType())) {
                    _uriFields.push_back(uriField);
                    uriField.markUsed(usedFields);
                } else if (uriField.broken(schema,
                                   field.getCollectionType())) {
                    // Broken removal of unused URI fields.
                    uriField.markUsed(usedFields);
                }
            }
        }
    }

    // Non-URI fields are currently supposed to be text fields.
    for (uint32_t fieldId = 0; fieldId < numIndexFields; ++fieldId) {
        if (usedFields[fieldId])
            continue;
        const Schema::IndexField &field = schema.getIndexField(fieldId);
        switch (field.getDataType()) {
        case schema::DataType::STRING:
            _textFields.push_back(fieldId);
            break;
        default:
            ;
        }
    }
}

DocTypeBuilder::DocTypeBuilder(const Schema &schema)
    : _schema(schema),
      _iFields()
{
    _iFields.setup(schema);
}

namespace {
using namespace document::config_builder;
TypeOrId makeCollection(TypeOrId datatype,
                        Schema::CollectionType collection_type) {
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
}  // namespace

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
        if (usf != usedFields.end())
            continue;   // taken as index field

        const DataType *primitiveType = convert(field.getDataType());
        header_struct.addField(field.getName(), type_cache.getType(
                        primitiveType->getId(), field.getCollectionType()));
        usedFields.insert(field.getName());
    }

    for (uint32_t i = 0; i < _schema.getNumSummaryFields(); ++i) {
        const Schema::SummaryField &field = _schema.getSummaryField(i);
        UsedFields::const_iterator usf = usedFields.find(field.getName());
        if (usf != usedFields.end())
            continue;   // taken as index field or attribute field
        const DataType *primitiveType(convert(field.getDataType()));
        header_struct.addField(field.getName(), type_cache.getType(
                        primitiveType->getId(), field.getCollectionType()));
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
