// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/fef/itablemanager.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/fieldtype.h>
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

    // inherit documentation
    virtual const search::fef::Properties & getProperties() const { return _properties; }

    // inherit documentation
    virtual uint32_t getNumFields() const { return _fields.size(); }

    // inherit documentation
    virtual const search::fef::FieldInfo * getField(uint32_t id) const {
        if (id >= _fields.size()) {
            return NULL;
        }
        return &_fields[id];
    }

    // inherit documentation
    virtual const search::fef::FieldInfo * getFieldByName(const string & name) const {
        StringInt32Map::const_iterator itr = _fieldNames.find(name);
        if (itr == _fieldNames.end()) {
            return NULL;
        }
        return getField(itr->second);
    }

    // inherit documentation
    virtual const search::fef::ITableManager & getTableManager() const { return *_tableManager; }

    virtual FeatureMotivation getFeatureMotivation() const override {
        return _motivation;
    }

    // inherit documentation
    virtual void hintFeatureMotivation(FeatureMotivation motivation) const {
        _motivation = motivation;
    }

    // inherit documentation
    virtual void hintFieldAccess(uint32_t) const {}

    // inherit documentation
    virtual void hintAttributeAccess(const string & name) const {
        if (name.empty()) {
            return;
        }
        if (_motivation == RANK) {
            _rankAttributes.insert(name);
        } else {
            _dumpAttributes.insert(name);
        }
    }

    bool addField(const vespalib::string & name, bool isAttribute);

    search::fef::Properties & getProperties() { return _properties; }

    const std::set<vespalib::string> & getHintedRankAttributes() const { return _rankAttributes; }

    const std::set<vespalib::string> & getHintedDumpAttributes() const { return _dumpAttributes; }
};

} // namespace storage

