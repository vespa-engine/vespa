// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_type_matcher.h"
#include <vespa/searchcommon/attribute/config.h>

using search::attribute::BasicType;

namespace proton
{

bool
AttributeTypeMatcher::operator()(const search::attribute::Config &oldConfig, const search::attribute::Config &newConfig) const
{
    if ((oldConfig.basicType() != newConfig.basicType()) ||
        (oldConfig.collectionType() != newConfig.collectionType())) {
        return false;
    }
    if (newConfig.basicType().type() == BasicType::Type::TENSOR) {
        if (oldConfig.tensorType() != newConfig.tensorType()) {
            return false;
        }
    }
    if (newConfig.basicType().type() == BasicType::Type::PREDICATE) {
        using Params = search::attribute::PersistentPredicateParams;
        const Params &oldParams = oldConfig.predicateParams();
        const Params &newParams = newConfig.predicateParams();
        if (!(oldParams == newParams)) {
            return false;
        }
    }
    return true;
}

}
