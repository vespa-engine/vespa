// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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

namespace storage {

/**
 * Implementation of the feature execution framework
 * index environment API for the search visitor.
 **/
class IndexEnvironment : public search::fef::IIndexEnvironment
{
private:
    typedef vespalib::hash_map<vespalib::string, uint32_t> StringInt32Map;
    const search::fef::ITableManager   * _tableManager;
    search::fef::Properties              _properties;
    std::vector<search::fef::FieldInfo>  _fields;
    StringInt32Map                       _fieldNames;
    mutable FeatureMotivation            _motivation;
    mutable std::set<vespalib::string>   _rankAttributes;
    mutable std::set<vespalib::string>   _dumpAttributes;

public:
    IndexEnvironment(const search::fef::ITableManager & tableManager);
    ~IndexEnvironment();

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

    void hintFieldAccess(uint32_t) const override {}

    void hintAttributeAccess(const string & name) const override;

    vespalib::eval::ConstantValue::UP getConstantValue(const vespalib::string &) const override {
        return vespalib::eval::ConstantValue::UP();
    }

    bool addField(const vespalib::string & name, bool isAttribute);

    search::fef::Properties & getProperties() { return _properties; }

    const std::set<vespalib::string> & getHintedRankAttributes() const { return _rankAttributes; }

    const std::set<vespalib::string> & getHintedDumpAttributes() const { return _dumpAttributes; }

};

} // namespace storage

