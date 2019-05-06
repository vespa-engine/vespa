// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attribute_utils.h"
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/singlenumericattribute.hpp>

namespace proton::test {

using Int32Attribute = search::SingleValueNumericAttribute<search::IntegerAttributeTemplate<int32_t> >;

inline std::unique_ptr<Int32Attribute>
createInt32Attribute(const vespalib::string &name) {
    return std::make_unique<Int32Attribute>(name, AttributeUtils::getInt32Config());
}

}
