// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attribute_map.h"
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/tablemanager.h>
#include <vespa/eval/eval/value_cache/constant_value.h>
#include <string>
#include <vector>

namespace search {
namespace fef {
namespace test {

/**
 * Implementation of the IIndexEnvironment interface used for testing.
 */
class IndexEnvironment : public IIndexEnvironment
{
public:
    struct Constant : vespalib::eval::ConstantValue {
        vespalib::eval::ValueType _type;
        std::unique_ptr<vespalib::eval::Value> _value;
        Constant(vespalib::eval::ValueType type,
                     std::unique_ptr<vespalib::eval::Value> value)
            : _type(std::move(type)), _value(std::move(value))
        { }
        Constant(Constant &&rhs)
            : _type(std::move(rhs._type)),
              _value(std::move(rhs._value))
        {
        }
        const vespalib::eval::ValueType &type() const override { return _type; }
        const vespalib::eval::Value &value() const override { return *_value; }
        ~Constant() { }
    };

    struct ConstantRef : vespalib::eval::ConstantValue {
        const Constant &_value;
        ConstantRef(const Constant &value)
            : _value(value)
        { }
        const vespalib::eval::ValueType &type() const override { return _value.type(); }
        const vespalib::eval::Value &value() const override { return _value.value(); }
        ~ConstantRef() { }
    };

    using ConstantsMap = std::map<vespalib::string, Constant>;
    /**
     * Constructs a new index environment.
     */
    IndexEnvironment();

    // Inherit doc from IIndexEnvironment.
    virtual const Properties &getProperties() const override { return _properties; }

    // Inherit doc from IIndexEnvironment.
    virtual uint32_t getNumFields() const override { return _fields.size(); }

    // Inherit doc from IIndexEnvironment.
    virtual const FieldInfo *getField(uint32_t id) const override;

    // Inherit doc from IIndexEnvironment.
    virtual const FieldInfo *getFieldByName(const string &name) const override;

    // Inherit doc from IIndexEnvironment.
    virtual const ITableManager &getTableManager() const override { return _tableMan; }

    // Inherit doc from IIndexEnvironment.
    virtual FeatureMotivation getFeatureMotivation() const override { return UNKNOWN; }

    // Inherit doc from IIndexEnvironment.
    virtual void hintFeatureMotivation(FeatureMotivation) const override {}

    // Inherit doc from IIndexEnvironment.
    virtual void hintFieldAccess(uint32_t) const override {}

    // Inherit doc from IIndexEnvironment.
    virtual void hintAttributeAccess(const string &) const override {}

    /** Returns a reference to the properties map of this. */
    Properties &getProperties() { return _properties; }

    /** Returns a reference to the list of fields of this. */
    std::vector<FieldInfo> &getFields() { return _fields; }

    /** Returns a const reference to the list of fields of this. */
    const std::vector<FieldInfo> &getFields() const { return _fields; }

    /** Returns a reference to the attribute map of this. */
    AttributeMap &getAttributeMap() { return _attrMap; }

    /** Returns a reference to the table manager of this. */
    TableManager &getTableManager() { return _tableMan; }

    virtual vespalib::eval::ConstantValue::UP getConstantValue(const vespalib::string &name) const override;

    void addConstantValue(const vespalib::string &name,
                          vespalib::eval::ValueType type,
                          std::unique_ptr<vespalib::eval::Value> value);

private:
    IndexEnvironment(const IndexEnvironment &);             // hide
    IndexEnvironment & operator=(const IndexEnvironment &); // hide

private:
    Properties             _properties;
    std::vector<FieldInfo> _fields;
    AttributeMap           _attrMap;
    TableManager           _tableMan;
    ConstantsMap           _constants;
};

} // namespace test
} // namespace fef
} // namespace search

