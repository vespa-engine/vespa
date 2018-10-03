// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_constant_value_repo.h"
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
    typedef std::map<string, uint32_t> FieldNameMap;
    search::fef::TableManager           _tableManager;
    search::fef::Properties             _properties;
    FieldNameMap                        _fieldNames;
    std::vector<search::fef::FieldInfo> _fields;
    mutable FeatureMotivation           _motivation;
    const IConstantValueRepo           &_constantValueRepo;

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
     * @param schema the index schema
     * @param props config
     * @param constantValueRepo repo used to access constant values for ranking
     **/
    IndexEnvironment(const search::index::Schema &schema,
                     const search::fef::Properties &props,
                     const IConstantValueRepo &constantValueRepo);

    const search::fef::Properties &getProperties() const override;
    uint32_t getNumFields() const override;
    const search::fef::FieldInfo *getField(uint32_t id) const override;
    const search::fef::FieldInfo *getFieldByName(const string &name) const override;
    const search::fef::ITableManager &getTableManager() const override;
    FeatureMotivation getFeatureMotivation() const override;
    void hintFeatureMotivation(FeatureMotivation motivation) const override;
    void hintFieldAccess(uint32_t fieldId) const override;
    void hintAttributeAccess(const string &name) const override;

    vespalib::eval::ConstantValue::UP getConstantValue(const vespalib::string &name) const override {
        return _constantValueRepo.getConstant(name);
    }

    ~IndexEnvironment() override;
};

}
