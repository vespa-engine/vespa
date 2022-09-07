// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attribute_map.h"
#include <vespa/searchlib/attribute/attributemanager.h>
#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/fef/properties.h>
#include <vespa/searchlib/fef/onnx_model.h>
#include <vespa/searchlib/fef/fieldinfo.h>
#include <vespa/searchlib/fef/tablemanager.h>
#include <vespa/eval/eval/value_cache/constant_value.h>
#include <string>
#include <vector>

namespace search::fef::test {

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
        Constant(Constant &&rhs) noexcept
            : _type(std::move(rhs._type)),
              _value(std::move(rhs._value))
        {
        }
        const vespalib::eval::ValueType &type() const override { return _type; }
        const vespalib::eval::Value &value() const override { return *_value; }
        ~Constant() override;
    };

    struct ConstantRef : vespalib::eval::ConstantValue {
        const Constant &_value;
        explicit ConstantRef(const Constant &value)
            : _value(value)
        { }
        const vespalib::eval::ValueType &type() const override { return _value.type(); }
        const vespalib::eval::Value &value() const override { return _value.value(); }
        ~ConstantRef() override = default;
    };

    using ConstantsMap = std::map<vespalib::string, Constant>;
    using ExprMap = std::map<vespalib::string, vespalib::string>;
    using ModelMap = std::map<vespalib::string, OnnxModel>;

    IndexEnvironment();
    IndexEnvironment(const IndexEnvironment &) = delete;
    IndexEnvironment & operator=(const IndexEnvironment &) = delete;
    ~IndexEnvironment() override;

    const Properties &getProperties() const override { return _properties; }
    uint32_t getNumFields() const override { return _fields.size(); }
    const FieldInfo *getField(uint32_t id) const override;
    const FieldInfo *getFieldByName(const string &name) const override;
    const ITableManager &getTableManager() const override { return _tableMan; }
    FeatureMotivation getFeatureMotivation() const override { return UNKNOWN; }
    void hintFeatureMotivation(FeatureMotivation) const override {}
    void hintFieldAccess(uint32_t) const override {}
    void hintAttributeAccess(const string &) const override {}
    uint32_t getDistributionKey() const override { return 3; }

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

    vespalib::eval::ConstantValue::UP getConstantValue(const vespalib::string &name) const override;

    void addConstantValue(const vespalib::string &name,
                          vespalib::eval::ValueType type,
                          std::unique_ptr<vespalib::eval::Value> value);

    vespalib::string getRankingExpression(const vespalib::string &name) const override;
    void addRankingExpression(const vespalib::string &name, const vespalib::string &value);

    const OnnxModel *getOnnxModel(const vespalib::string &name) const override;
    void addOnnxModel(OnnxModel model);

private:
    Properties             _properties;
    std::vector<FieldInfo> _fields;
    AttributeMap           _attrMap;
    TableManager           _tableMan;
    ConstantsMap           _constants;
    ExprMap                _expressions;
    ModelMap               _models;
};

}
