// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/singlenumericattribute.h>

namespace proton::test {

using Int32Attribute = search::SingleValueNumericAttribute<search::IntegerAttributeTemplate<int32_t> >;

std::unique_ptr<Int32Attribute> createInt32Attribute(const vespalib::string &name);

}
