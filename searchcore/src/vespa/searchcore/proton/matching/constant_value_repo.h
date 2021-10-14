// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_constant_value_repo.h"
#include "ranking_constants.h"
#include <vespa/eval/eval/value_cache/constant_value.h>

namespace proton::matching {

/**
 * Class that provides access to a configured set of rank constant values.
 *
 * This class maps the symbolic name used by rank features to the file path (where the constant is stored) and its type,
 * and uses a factory to instantiate the actual constant values.
 */
class ConstantValueRepo : public IConstantValueRepo {
private:
    using ConstantValueFactory = vespalib::eval::ConstantValueFactory;

    const ConstantValueFactory &_factory;
    RankingConstants _constants;

public:
    ConstantValueRepo(const ConstantValueFactory &factory);
    void reconfigure(const RankingConstants &constants);
    virtual vespalib::eval::ConstantValue::UP getConstant(const vespalib::string &name) const override;
};

}
