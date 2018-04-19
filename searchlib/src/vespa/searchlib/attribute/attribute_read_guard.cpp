// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "attribute_read_guard.h"

namespace search::attribute {

AttributeReadGuard::AttributeReadGuard(const IAttributeVector *attr)
    : _attr(attr)
{
}

AttributeReadGuard::~AttributeReadGuard()
{
}

}
