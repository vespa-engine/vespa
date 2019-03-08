// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_utils.h"
#include <vespa/searchcommon/attribute/config.h>

namespace proton::attribute {

bool
isUpdateableInMemoryOnly(const vespalib::string &attrName,
                         const search::attribute::Config &cfg)
{
    auto basicType = cfg.basicType().type();
    return ((basicType != search::attribute::BasicType::Type::PREDICATE) &&
            (basicType != search::attribute::BasicType::Type::REFERENCE)) &&
            !isStructFieldAttribute(attrName);
}

bool
isStructFieldAttribute(const vespalib::string &attrName)
{
    return attrName.find('.') != vespalib::string::npos;
}

}
