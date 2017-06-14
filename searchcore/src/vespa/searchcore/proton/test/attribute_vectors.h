// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "attribute_utils.h"
#include <vespa/searchlib/attribute/integerbase.h>
#include <vespa/searchlib/attribute/singlenumericattribute.hpp>

namespace proton {

namespace test {

typedef search::SingleValueNumericAttribute<search::IntegerAttributeTemplate<int32_t> > Int32AttributeBase;

struct Int32Attribute : public Int32AttributeBase
{
    Int32Attribute(const vespalib::string &name) :
        Int32AttributeBase(name, AttributeUtils::getInt32Config())
    {
    }
};

} // namespace test

} // namespace proton
