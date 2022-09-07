// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexenvironment.h"

#include <vespa/searchlib/fef/functiontablefactory.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>

using namespace search::fef;

namespace proton::matching {

void
IndexEnvironment::extractFields(const search::index::Schema &schema)
{
    using SchemaField = search::index::Schema::Field;
    for (uint32_t i = 0; i < schema.getNumAttributeFields(); ++i) {
        const SchemaField &field = schema.getAttributeField(i);
        FieldInfo fieldInfo(FieldType::ATTRIBUTE, field.getCollectionType(), field.getName(), _fields.size());
        fieldInfo.set_data_type(field.getDataType());
        insertField(fieldInfo);
    }
    for (uint32_t i = 0; i < schema.getNumIndexFields(); ++i) {
        const SchemaField &field = schema.getIndexField(i);
        FieldInfo fieldInfo(FieldType::INDEX, field.getCollectionType(), field.getName(), _fields.size());
        fieldInfo.set_data_type(field.getDataType());
        if (indexproperties::IsFilterField::check(_properties, field.getName())) {
            fieldInfo.setFilter(true);
        }
        auto itr = _fieldNames.find(field.getName());
        if (itr != _fieldNames.end()) { // override the attribute field
            FieldInfo shadow_field(fieldInfo.type(), fieldInfo.collection(), fieldInfo.name(), itr->second);
            shadow_field.set_data_type(fieldInfo.get_data_type());
            shadow_field.addAttribute(); // tell ranking about the shadowed attribute
            _fields[itr->second] = shadow_field;
        } else {
            insertField(fieldInfo);
        }
    }
    for (const auto &attr : schema.getImportedAttributeFields()) {
        FieldInfo field(FieldType::ATTRIBUTE, attr.getCollectionType(), attr.getName(), _fields.size());
        field.set_data_type(attr.getDataType());
        insertField(field);
    }

    //TODO: This is a kludge to get [documentmetastore] searchable
    {
        FieldInfo fieldInfo(FieldType::HIDDEN_ATTRIBUTE, FieldInfo::CollectionType::SINGLE,
                            DocumentMetaStore::getFixedName(), _fields.size());
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

IndexEnvironment::IndexEnvironment(uint32_t distributionKey,
                                   const search::index::Schema &schema,
                                   search::fef::Properties props,
                                   const IRankingAssetsRepo &rankingAssetsRepo)
  : _tableManager(),
    _properties(std::move(props)),
    _fieldNames(),
    _fields(),
    _motivation(UNKNOWN),
    _rankingAssetsRepo(rankingAssetsRepo),
    _distributionKey(distributionKey)
{
    _tableManager.addFactory(std::make_shared<search::fef::FunctionTableFactory>(256));
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
    return nullptr;
}

const search::fef::FieldInfo *
IndexEnvironment::getFieldByName(const string &name) const
{
    auto pos = _fieldNames.find(name);
    if (pos == _fieldNames.end()) {
        return nullptr;
    }
    return getField(pos->second);
}

const search::fef::ITableManager &
IndexEnvironment::getTableManager() const {
    return _tableManager;
}

IIndexEnvironment::FeatureMotivation
IndexEnvironment::getFeatureMotivation() const {
    return _motivation;
}

void
IndexEnvironment::hintFeatureMotivation(FeatureMotivation motivation) const {
    _motivation = motivation;
}

void
IndexEnvironment::hintFieldAccess(uint32_t ) const { }

void
IndexEnvironment::hintAttributeAccess(const string &) const { }

IndexEnvironment::~IndexEnvironment() = default;

}
