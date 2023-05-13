// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/i_ranking_assets_repo.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/tablemanager.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/eval/eval/value_cache/constant_value.h>

namespace proton::matching {

/**
 * Index environment implementation for the proton matching pipeline.
 **/
class IndexEnvironment : public search::fef::IIndexEnvironment
{
private:
    using FieldNameMap = vespalib::hash_map<string, uint32_t>;
    search::fef::TableManager              _tableManager;
    search::fef::Properties                _properties;
    FieldNameMap                           _fieldNames;
    std::vector<search::fef::FieldInfo>    _fields;
    mutable FeatureMotivation              _motivation;
    const search::fef::IRankingAssetsRepo& _rankingAssetsRepo;
    uint32_t                               _distributionKey;


    /**
     * Extract field information from the given schema and populate
     * this index environment.
     **/
    void extractFields(const search::index::Schema &schema);
    void insertField(const search::fef::FieldInfo &field);

public:
    /**
     * Sets up this index environment based on the given schema and
     * properties.
     *
     * @param distributionKey the distribution key for this node.
     * @param schema the index schema
     * @param props config
     * @param constantValueRepo repo used to access constant values for ranking
     * @param rankingExpressions processed config about ranking expressions
     * @param onnxModels processed config about onnx models
     **/
    IndexEnvironment(uint32_t distributionKey,
                     const search::index::Schema &schema,
                     search::fef::Properties props,
                     const search::fef::IRankingAssetsRepo& constantValueRepo);
    ~IndexEnvironment() override;


    const search::fef::Properties &getProperties() const override;
    uint32_t getNumFields() const override;
    const search::fef::FieldInfo *getField(uint32_t id) const override;
    const search::fef::FieldInfo *getFieldByName(const string &name) const override;
    const search::fef::ITableManager &getTableManager() const override;
    FeatureMotivation getFeatureMotivation() const override;
    void hintFeatureMotivation(FeatureMotivation motivation) const override;
    uint32_t getDistributionKey() const override { return _distributionKey; }

    vespalib::eval::ConstantValue::UP getConstantValue(const vespalib::string &name) const override {
        return _rankingAssetsRepo.getConstant(name);
    }
    vespalib::string getRankingExpression(const vespalib::string &name) const override {
        return _rankingAssetsRepo.getExpression(name);
    }

    const search::fef::OnnxModel *getOnnxModel(const vespalib::string &name) const override {
        return _rankingAssetsRepo.getOnnxModel(name);
    }
};

}
