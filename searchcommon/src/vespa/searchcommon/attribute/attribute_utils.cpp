// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_utils.h"
#include <vespa/searchcommon/attribute/config.h>

namespace search::attribute {

bool
isUpdateableInMemoryOnly(const vespalib::string &attrName, const Config &cfg)
{
    auto basicType = cfg.basicType().type();
    return ((basicType != BasicType::Type::PREDICATE) &&
            (basicType != BasicType::Type::REFERENCE)) &&
            !isStructFieldAttribute(attrName);
}

bool
isStructFieldAttribute(const vespalib::string &attrName)
{
    return attrName.find('.') != vespalib::string::npos;
}

}
