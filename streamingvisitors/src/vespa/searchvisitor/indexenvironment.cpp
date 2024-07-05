// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexenvironment.h"
#include <vespa/searchlib/fef/i_ranking_assets_repo.h>
#include <vespa/searchlib/fef/indexproperties.h>
#include <vespa/vespalib/stllike/hash_set.h>

using namespace search::fef;

namespace streaming {

IndexEnvironment::IndexEnvironment(const ITableManager & tableManager) :
    _tableManager(&tableManager),
    _properties(),
    _fields(),
    _fieldNames(),
    _motivation(RANK),
    _ranking_assets_repo()
{
}

IndexEnvironment::IndexEnvironment(const IndexEnvironment &) = default;
IndexEnvironment::IndexEnvironment(IndexEnvironment &&) noexcept = default;
IndexEnvironment::~IndexEnvironment() = default;

bool
IndexEnvironment::addField(const vespalib::string& name,
                           bool isAttribute,
                           search::fef::FieldInfo::DataType data_type)
{
    if (getFieldByName(name) != nullptr) {
        return false;
    }
    FieldInfo info(isAttribute ? FieldType::ATTRIBUTE : FieldType::INDEX,
                   FieldInfo::CollectionType::SINGLE, name, _fields.size());
    info.set_data_type(data_type);
    info.addAttribute(); // we are able to produce needed attributes at query time
    _fields.push_back(info);
    _fieldNames[info.name()] = info.id();
    return true;
}

/*
 * Ensure that array and map fields are known by the index
 * environment, allowing the matches features to be used with the
 * sameElement query operator. FieldSearchSpecMap::buildFromConfig()
 * propagates the name to field id mapping for the added virtual
 * fields.
 */
void
IndexEnvironment::add_virtual_fields()
{
    vespalib::hash_set<vespalib::string> vfields;
    for (auto& field : _fields) {
        std::string_view name(field.name());
        auto pos = name.rfind('.');
        while (pos != vespalib::string::npos) {
            name = name.substr(0, pos);
            if (_fieldNames.contains(name)) {
                break;
            }
            vfields.insert(name);
            pos = name.rfind('.');
        }
    }
    for (auto& vfield : vfields) {
        FieldInfo info(FieldType::VIRTUAL, FieldInfo::CollectionType::ARRAY, vfield, _fields.size());
        info.set_data_type(FieldInfo::DataType::COMBINED);
        _fields.push_back(info);
        _fieldNames[vfield] = info.id();
    }
}

void
IndexEnvironment::fixup_fields()
{
    for (auto& field : _fields) {
        if (indexproperties::IsFilterField::check(_properties, field.name())) {
            field.setFilter(true);
	}
    }
}

void
IndexEnvironment::set_ranking_assets_repo(std::shared_ptr<const IRankingAssetsRepo> ranking_assets_repo)
{
    _ranking_assets_repo = std::move(ranking_assets_repo);
}

vespalib::eval::ConstantValue::UP
IndexEnvironment::getConstantValue(const vespalib::string& name) const
{
    return _ranking_assets_repo->getConstant(name);
}

vespalib::string
IndexEnvironment::getRankingExpression(const vespalib::string& name) const
{
    return _ranking_assets_repo->getExpression(name);
}

const search::fef::OnnxModel*
IndexEnvironment::getOnnxModel(const vespalib::string& name) const
{
    return _ranking_assets_repo->getOnnxModel(name);
}

}
