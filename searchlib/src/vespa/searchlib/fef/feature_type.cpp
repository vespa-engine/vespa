// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "feature_type.h"

#include <vespa/check_require.h>

namespace search::fef {

const FeatureType FeatureType::_number = FeatureType(TYPE_UP());

FeatureType::FeatureType(const FeatureType& rhs) : _type() {
    if (rhs.is_object()) {
        _type = std::make_unique<TYPE>(rhs.type());
    }
}

FeatureType FeatureType::object(const TYPE& type_in) {
    return FeatureType(std::make_unique<TYPE>(type_in));
}

const FeatureType::TYPE& FeatureType::type() const {
    CHECK(_type);
    return *_type;
}

} // namespace search::fef
