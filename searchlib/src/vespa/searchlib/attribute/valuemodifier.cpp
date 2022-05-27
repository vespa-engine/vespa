// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "valuemodifier.h"
#include "attributevector.h"

namespace search::attribute {

ValueModifier::ValueModifier(AttributeVector &attr)
    : _attr(&attr)
{ }

ValueModifier::~ValueModifier() {
    if (_attr) {
        _attr->incGeneration();
    }
}

}
