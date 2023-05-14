// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexenvironment.h"
#include <vespa/searchlib/fef/i_ranking_assets_repo.h>

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
