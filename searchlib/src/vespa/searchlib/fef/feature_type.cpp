// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include "feature_type.h"

namespace search {
namespace fef {

const FeatureType FeatureType::_number = FeatureType(TYPE_UP());

FeatureType::FeatureType(const FeatureType &rhs)
    : _type()
{
    if (rhs.is_object()) {
        _type = std::make_unique<TYPE>(rhs.type());
    }
}

FeatureType
FeatureType::object(const TYPE &type_in)
{
    return FeatureType(std::make_unique<TYPE>(type_in));
}

} // namespace fef
} // namespace search
