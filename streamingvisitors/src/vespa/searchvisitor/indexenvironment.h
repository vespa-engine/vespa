// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/fef/itablemanager.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/fieldtype.h>
#include <vespa/eval/eval/value_cache/constant_value.h>
#include <vespa/vespalib/stllike/string.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <set>

namespace search::fef { struct IRankingAssetsRepo; }

namespace streaming {

/**
 * Implementation of the feature execution framework
 * index environment API for the search visitor.
 **/
class IndexEnvironment : public search::fef::IIndexEnvironment
{
private:
    using StringInt32Map = vespalib::hash_map<vespalib::string, uint32_t>;
    const search::fef::ITableManager   * _tableManager;
    search::fef::Properties              _properties;
    std::vector<search::fef::FieldInfo>  _fields;
    StringInt32Map                       _fieldNames;
    mutable FeatureMotivation            _motivation;
    std::shared_ptr<const search::fef::IRankingAssetsRepo> _ranking_assets_repo;

public:
    IndexEnvironment(const search::fef::ITableManager & tableManager);
    IndexEnvironment(IndexEnvironment &&) noexcept;
    IndexEnvironment(const IndexEnvironment &);
    ~IndexEnvironment() override;

    const search::fef::Properties & getProperties() const override { return _properties; }

    uint32_t getNumFields() const override { return _fields.size(); }

    const search::fef::FieldInfo * getField(uint32_t id) const override {
        if (id >= _fields.size()) {
            return nullptr;
        }
        return &_fields[id];
    }

    const search::fef::FieldInfo * getFieldByName(const string & name) const override {
        auto itr = _fieldNames.find(name);
        if (itr == _fieldNames.end()) {
            return nullptr;
        }
        return getField(itr->second);
    }

    const search::fef::ITableManager & getTableManager() const override {
        return *_tableManager;
    }

    FeatureMotivation getFeatureMotivation() const override {
        return _motivation;
    }

    void hintFeatureMotivation(FeatureMotivation motivation) const override {
        _motivation = motivation;
    }

    vespalib::eval::ConstantValue::UP getConstantValue(const vespalib::string& name) const override;

    vespalib::string getRankingExpression(const vespalib::string& name) const override;

    const search::fef::OnnxModel *getOnnxModel(const vespalib::string& name) const override;

    bool addField(const vespalib::string& name,
                  bool isAttribute,
                  search::fef::FieldInfo::DataType data_type);

    search::fef::Properties & getProperties() { return _properties; }

    void set_ranking_assets_repo(std::shared_ptr<const search::fef::IRankingAssetsRepo> ranking_assets_repo);

    //TODO Wire in proper distribution key
    uint32_t getDistributionKey() const override { return 0; }

};

} // namespace streaming

