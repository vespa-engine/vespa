// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "indexenvironment.h"
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/vespalib/util/stringfmt.h>

namespace search::fef::test {

using vespalib::eval::ValueType;

IndexEnvironment::IndexEnvironment() = default;

IndexEnvironment::~IndexEnvironment() = default;
IndexEnvironment::Constant::~Constant() = default;

const FieldInfo *
IndexEnvironment::getField(uint32_t id) const
{
    return id < _fields.size() ? &_fields[id] : nullptr;
}

const FieldInfo *
IndexEnvironment::getFieldByName(const string &name) const
{
    for (const auto & field : _fields) {
        if (field.name() == name) {
            return &field;
        }
    }
    return nullptr;
}


vespalib::eval::ConstantValue::UP
IndexEnvironment::getConstantValue(const vespalib::string &name) const
{
    auto it = _constants.find(name);
    if (it != _constants.end()) {
        return std::make_unique<ConstantRef>(it->second);
    } else {
        return {nullptr};
    }
}

void
IndexEnvironment::addConstantValue(const vespalib::string &name,
                                   vespalib::eval::ValueType type,
                                   std::unique_ptr<vespalib::eval::Value> value)
{
    auto insertRes = _constants.emplace(name, Constant(std::move(type), std::move(value)));
    assert(insertRes.second); // successful insert
    (void) insertRes;
}

vespalib::string
IndexEnvironment::getRankingExpression(const vespalib::string &name) const
{
    auto pos = _expressions.find(name);
    if (pos != _expressions.end()) {
        return pos->second;
    }
    return {};
}

void
IndexEnvironment::addRankingExpression(const vespalib::string &name, const vespalib::string &value)
{
    _expressions.insert_or_assign(name, value);    
}

const OnnxModel *
IndexEnvironment::getOnnxModel(const vespalib::string &name) const
{
    auto pos = _models.find(name);
    if (pos != _models.end()) {
        return &pos->second;
    }
    return nullptr;
}

void
IndexEnvironment::addOnnxModel(OnnxModel model)
{
    _models.insert_or_assign(model.name(), std::move(model));
}


}
