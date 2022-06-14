// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "interlock.h"

namespace search { class AttributeVector; }

namespace search::attribute {

class ValueModifier
{
public:
    ValueModifier(AttributeVector &attr);
    ValueModifier(const ValueModifier &) = delete;
    ValueModifier & operator = (const ValueModifier &) = delete;
    ~ValueModifier();
private:
    AttributeVector * _attr;
};

}
