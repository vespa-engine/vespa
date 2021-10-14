// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "constant_value_repo.h"

using vespalib::eval::ConstantValue;

namespace proton::matching {

ConstantValueRepo::ConstantValueRepo(const ConstantValueFactory &factory)
    : _factory(factory),
      _constants()
{
}

void
ConstantValueRepo::reconfigure(const RankingConstants &constants)
{
    _constants = constants;
}

ConstantValue::UP
ConstantValueRepo::getConstant(const vespalib::string &name) const
{
    const RankingConstants::Constant *constant = _constants.getConstant(name);
    if (constant != nullptr) {
        return _factory.create(constant->filePath, constant->type);
    }
    return ConstantValue::UP(nullptr);
}

}
