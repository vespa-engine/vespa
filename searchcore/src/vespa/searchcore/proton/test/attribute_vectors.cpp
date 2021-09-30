// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_vectors.h"
#include "attribute_utils.h"

namespace proton::test {

std::unique_ptr<Int32Attribute>
createInt32Attribute(const vespalib::string &name) {
    return std::make_unique<Int32Attribute>(name, AttributeUtils::getInt32Config());
}

}
