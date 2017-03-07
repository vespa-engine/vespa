// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexenvironment.h"

#include <vespa/searchlib/fef/functiontablefactory.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>

using namespace search::fef;

namespace proton {
namespace matching {

namespace {

search::fef::CollectionType convertCollectionType(search::index::Schema::CollectionType type) {
    switch (type) {
    case search::index::schema::SINGLE:      return search::fef::CollectionType::SINGLE;
    case search::index::schema::ARRAY:       return search::fef::CollectionType::ARRAY;
    case search::index::schema::WEIGHTEDSET: return search::fef::CollectionType::WEIGHTEDSET;
    default:
        abort();
    }
}

}

void
IndexEnvironment::extractFields(const search::index::Schema &schema)
{
    typedef search::index::Schema::Field SchemaField;
    for (uint32_t i = 0; i < schema.getNumAttributeFields(); ++i) {
        const SchemaField &field = schema.getAttributeField(i);
        search::fef::FieldInfo fieldInfo(search::fef::FieldType::ATTRIBUTE,
                                         convertCollectionType(field.getCollectionType()),
                                         field.getName(), _fields.size());
        fieldInfo.set_data_type(field.getDataType());
        insertField(fieldInfo);
    }
    for (uint32_t i = 0; i < schema.getNumIndexFields(); ++i) {
        const SchemaField &field = schema.getIndexField(i);
        search::fef::FieldInfo fieldInfo(search::fef::FieldType::INDEX,
                                         convertCollectionType(field.getCollectionType()),
                                         field.getName(), _fields.size());
        fieldInfo.set_data_type(field.getDataType());
        if (indexproperties::IsFilterField::check(
                    _properties, field.getName()))
        {
            fieldInfo.setFilter(true);
        }
        FieldNameMap::const_iterator itr = _fieldNames.find(field.getName());
        if (itr != _fieldNames.end()) { // override the attribute field
            FieldInfo shadow_field(fieldInfo.type(),
                                   fieldInfo.collection(),
                                   fieldInfo.name(),
                                   itr->second);
            shadow_field.set_data_type(fieldInfo.get_data_type());
            shadow_field.addAttribute(); // tell ranking about the shadowed attribute
            _fields[itr->second] = shadow_field;
        } else {
            insertField(fieldInfo);
        }
    }
    for (const auto &attr : schema.getImportedAttributeFields()) {
        search::fef::FieldInfo field(search::fef::FieldType::ATTRIBUTE,
                                     convertCollectionType(attr.getCollectionType()),
                                     attr.getName(), _fields.size());
        field.set_data_type(attr.getDataType());
        insertField(field);
    }

    //TODO: This is a kludge to get [documentmetastore] searchable
    {
        search::fef::FieldInfo fieldInfo(search::fef::FieldType::HIDDEN_ATTRIBUTE,
                                         search::fef::CollectionType::SINGLE,
                                         DocumentMetaStore::getFixedName(),
                                         _fields.size());
        fieldInfo.set_data_type(FieldInfo::DataType::RAW);
        fieldInfo.setFilter(true);
        insertField(fieldInfo);
    }
}

void
IndexEnvironment::insertField(const search::fef::FieldInfo &field)
{
    assert(field.id() == _fields.size());
    _fieldNames[field.name()] = _fields.size();
    _fields.push_back(field);
}

IndexEnvironment::IndexEnvironment(const search::index::Schema &schema,
                                   const search::fef::Properties &props,
                                   const IConstantValueRepo &constantValueRepo)
    : _tableManager(),
      _properties(props),
      _fieldNames(),
      _fields(),
      _motivation(UNKNOWN),
      _constantValueRepo(constantValueRepo)
{
    _tableManager.addFactory(
            search::fef::ITableFactory::SP(
                    new search::fef::FunctionTableFactory(256)));
    extractFields(schema);
}

const search::fef::Properties &
IndexEnvironment::getProperties() const
{
    return _properties;
}

uint32_t
IndexEnvironment::getNumFields() const
{
    return _fields.size();
}

const search::fef::FieldInfo *
IndexEnvironment::getField(uint32_t id) const
{
    if (id < _fields.size()) {
        return &_fields[id];
    }
    return 0;
}

const search::fef::FieldInfo *
IndexEnvironment::getFieldByName(const string &name) const
{
    typedef std::map<string, uint32_t>::const_iterator ITR;
    ITR pos = _fieldNames.find(name);
    if (pos == _fieldNames.end()) {
        return 0;
    }
    return getField(pos->second);
}

const search::fef::ITableManager &
IndexEnvironment::getTableManager() const
{
    return _tableManager;
}

IIndexEnvironment::FeatureMotivation
IndexEnvironment::getFeatureMotivation() const
{
    return _motivation;
}

void
IndexEnvironment::hintFeatureMotivation(FeatureMotivation motivation) const
{
    _motivation = motivation;
}

void
IndexEnvironment::hintFieldAccess(uint32_t fieldId) const
{
    (void) fieldId;
}

void
IndexEnvironment::hintAttributeAccess(const string &name) const
{
    (void) name;
}

IndexEnvironment::~IndexEnvironment()
{
}

} // namespace matching
} // namespace proton
