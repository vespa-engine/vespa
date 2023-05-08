// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/eval/eval/value_cache/constant_value.h>

namespace search::fef {

class OnnxModel;

/**
 * Interface for retrieving named constants, expressions and models from ranking.
 * Empty strings or nullptrs indicates nothing found.
 */
struct IRankingAssetsRepo {
    virtual vespalib::eval::ConstantValue::UP getConstant(const vespalib::string &name) const = 0;
    virtual vespalib::string getExpression(const vespalib::string &name) const = 0;
    virtual const search::fef::OnnxModel *getOnnxModel(const vespalib::string &name) const = 0;
    virtual ~IRankingAssetsRepo() = default;
};

}
