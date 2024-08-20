// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_utils.h"
#include "config.h"

namespace search::attribute {

bool
isUpdateableInMemoryOnly(const std::string &attrName, const Config &cfg)
{
    auto basicType = cfg.basicType().type();
    return ((basicType != BasicType::Type::PREDICATE) &&
            (basicType != BasicType::Type::REFERENCE)) &&
            !isStructFieldAttribute(attrName);
}

bool
isStructFieldAttribute(const std::string &attrName)
{
    return attrName.find('.') != std::string::npos;
}

}
